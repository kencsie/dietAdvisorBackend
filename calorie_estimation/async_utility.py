import torch
import cv2
import numpy as np
import joblib
import pandas as pd
import json
import io
from PIL import Image
import threading

import warnings
warnings.filterwarnings("ignore")

# Add your model's directory to the system path
import sys
import os

sys.path.append(os.path.abspath('./data/models/Depth-Anything/metric_depth'))
from depth_to_pointcloud import depth_estimation, process_images  # Ensure this is correctly imported

from segment_anything import sam_model_registry, SamPredictor

# Constants
CHECKPOINT_PATH = "./data/models/sam_vit_h_4b8939.pth"
MODEL_TYPE = "vit_h"
REAL_COIN_AREA = 13 ** 2 * np.pi

def resize_and_save_image(image_bytes):
    # Open the image from bytes
    with io.BytesIO(image_bytes) as img_buffer:
        with Image.open(img_buffer) as img:
            img = img.resize((640, 640), 1)
            output_buffer = io.BytesIO()
            img.save(output_buffer, format='PNG')
            modified_image_bytes = output_buffer.getvalue()
    return modified_image_bytes

class ModelManager:
    def __init__(self):
        self.devices = [torch.device(f'cuda:{i}') for i in range(torch.cuda.device_count())]
        self.models = {}
        self.lock = threading.Lock()

        # Load models on each device
        for device in self.devices:
            self.models[device] = {
                'sam_model': self.load_sam_model(device),
                'depth_model': self.load_depth_model(device),
                'yolo_model': self.load_yolo_model(device),
                'regression_model': self.load_regression_model()
            }

    def load_sam_model(self, device):
        # Load SAM model
        sam_model = sam_model_registry[MODEL_TYPE](checkpoint=CHECKPOINT_PATH).to(device=device)
        return sam_model

    def load_depth_model(self, device):
        # Load depth estimation model
        from zoedepth.models.builder import build_model
        from zoedepth.utils.config import get_config
        model_name = 'zoedepth'
        DATASET = 'nyu'  # Replace with your dataset name
        config = get_config(model_name, 'eval', DATASET)
        pretrained_resource = 'local::data/models/Depth-Anything/metric_depth/checkpoints/nutrition5k_03-May_12-04-b56f6cfdfe15_latest.pt'
        config.pretrained_resource = pretrained_resource
        depth_model = build_model(config).to(device)
        depth_model.eval()
        return depth_model

    def load_regression_model(self):
        # Load regression model (assumed to be CPU-based)
        regression_model = joblib.load('./data/models/regression_model.pkl')
        return regression_model

    def load_yolo_model(self, device):
        yolo_model = torch.hub.load('ultralytics/yolov5', 'custom', path='./data/models/yolo.pt', device=device)
        return yolo_model

    def get_device(self):
        # Simple device selection logic
        with self.lock:
            # Round-robin selection
            device = self.devices.pop(0)
            self.devices.append(device)
        return device

    def get_models(self, device):
        return self.models[device]

class CalorieEstimator:
    def __init__(self, models, device):
        self.device = device
        self.sam_model = models['sam_model']
        self.mask_predictor = SamPredictor(self.sam_model)
        self.depth_model = models['depth_model']
        self.yolo_model = models['yolo_model']
        self.regression_model = models['regression_model']

    def yolo_object_detection(self, image_bytes):
        self.yolo_model.eval()
        image = Image.open(io.BytesIO(image_bytes))
        results = self.yolo_model(image)
        detected_objects = []

        # Convert predictions into padnas dataframe
        predictions_df = results.pandas().xyxy[0]

        # Obtain names of the detected classes from the image
        # detected_classes = list()
        for index, row in predictions_df.iterrows():
            # detected_classes.append(row['name'])
            detected_object = {
                'class': row['class'],
                'name': row['name'],
                'confidence': row['confidence'],
                'bbox': [int(row['xmin']), int(row['ymin']), int(row['xmax']), int(row['ymax'])]
            }
            detected_objects.append(detected_object)
            
        return detected_objects

    def perform_segmentation(self, image_bytes, bounding_boxes, real_coin_area=REAL_COIN_AREA):
        # Load and prepare the image
        image = Image.open(io.BytesIO(image_bytes))
        image_np = np.array(image)
        image_rgb = cv2.cvtColor(image_np, cv2.COLOR_BGR2RGB)
        self.mask_predictor.set_image(image_rgb)

        details = {}
        coin_image_area = None
        confidence = 0

        for obj in bounding_boxes:
            bbox = np.array(obj['bbox'])
            masks, scores, _ = self.mask_predictor.predict(box=bbox, multimask_output=False)
            area = np.count_nonzero(masks[0])
            details[obj['name']] = {'mask': masks[0], 'image_area': area, 'object_id': obj['class']}
            if obj['name'] == 'coin' and float(obj['confidence']) > confidence:
                coin_image_area = area
                confidence = float(obj['confidence'])

        if coin_image_area:
            scale_factor = real_coin_area / coin_image_area
            for key, value in details.items():
                if key != 'coin':
                    image_area = value['image_area']
                    real_area = scale_factor * image_area
                    value['real_life_area'] = real_area
                value['scale_factor'] = scale_factor

        return details

    def calculate_volume_and_mass(self, segmentation_details, depth_map):
        depth_map = np.array(depth_map)
        depth_map *= 100  # Convert to centimeters

        results = []
        regression_input = {'object_id': [], 'area': [], 'volume': []}
        names = []
        cam_plane_to_coin = None
        coin_to_plate = 25  # Adjust this value as needed

        coin_details = segmentation_details.get('coin')
        if coin_details:
            coin_mask = coin_details['mask']
            coin_x_centroid, coin_y_centroid = self.coin_centroid(coin_mask)
            if coin_x_centroid is not None and coin_y_centroid is not None:
                cam_plane_to_coin = depth_map[coin_x_centroid, coin_y_centroid]

        for key, details in segmentation_details.items():
            if key == 'coin':
                continue

            names.append(key)
            mask = details['mask']
            area = details['real_life_area']
            object_id = details['object_id']
            regression_input['object_id'].append(object_id)
            regression_input['area'].append(area)

            masked_depth_map = np.where(mask, depth_map, 0)
            non_zero_depths = masked_depth_map[masked_depth_map != 0]

            if non_zero_depths.size > 0 and cam_plane_to_coin is not None:
                food_depths = np.maximum(0, cam_plane_to_coin - non_zero_depths)
                food_depths = np.maximum(0, food_depths - coin_to_plate)
                volume = np.sum(food_depths) * details['scale_factor']
                regression_input['volume'].append(volume)
            else:
                regression_input['volume'].append(0)

        regression_input_df = pd.DataFrame.from_dict(regression_input)
        pred = self.regression_model.predict(regression_input_df)

        for name, mass in zip(names, pred):
            results.append({'name': name, 'mass': mass})

        return results

    def coin_centroid(self, mask):
        indices = np.argwhere(mask > 0)
        if indices.size == 0:
            return None, None
        y_centroid, x_centroid = np.mean(indices, axis=0)
        return int(x_centroid), int(y_centroid)

    def depth_estimation(self, image_bytes):
        # Implement depth estimation using the preloaded depth_model
        depth_map = process_images(self.depth_model, image_bytes, self.device)
        return depth_map

    def calorie_estimation(self, image_bytes, detected_objects):
        image_bytes = resize_and_save_image(image_bytes)
        segmentation_details = self.perform_segmentation(image_bytes, detected_objects)
        depth_maps = self.depth_estimation(image_bytes)
        results = self.calculate_volume_and_mass(segmentation_details, depth_maps)
        torch.cuda.empty_cache()
        return results

# Initialize ModelManager
model_manager = ModelManager()

def calorie_estimation(image_bytes, detected_objects):
    # Get device and models
    device = model_manager.get_device()
    models = model_manager.get_models(device)
    
    estimator = CalorieEstimator(models, device)
    result = estimator.calorie_estimation(image_bytes, detected_objects)
    #torch.cuda.empty_cache()
    return result

def yolo_detection(image_bytes):
    # Get device and models
    device = model_manager.get_device()
    models = model_manager.get_models(device)
    
    estimator = CalorieEstimator(models, device)
    result = estimator.yolo_object_detection(image_bytes)
    #torch.cuda.empty_cache()
    return result