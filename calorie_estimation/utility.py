from werkzeug.utils import secure_filename
import os
import json
import torch
import cv2
import numpy as np
import subprocess
import joblib
import pandas as pd
from urllib.parse import quote, unquote
from PIL import Image
import io
import torchvision.transforms as transforms
from segment_anything import sam_model_registry, SamPredictor
import sys
import os

sys.path.append(os.path.abspath('./data/models/Depth-Anything/metric_depth'))
from depth_to_pointcloud import depth_estimation

def resize_and_save_image(image_bytes):
    # Open the image from bytes
    with io.BytesIO(image_bytes) as img_buffer:
        with Image.open(img_buffer) as img:
            img = img.resize((640, 640), 1)
            output_buffer = io.BytesIO()
            img.save(output_buffer, format='PNG')
            modified_image_bytes = output_buffer.getvalue()
    
    return modified_image_bytes

def yolo_object_detection(image_bytes):
    yolo_model = torch.hub.load('ultralytics/yolov5', 'custom', path='./data/models/yolo.pt')
    yolo_model.eval()

    image = Image.open(io.BytesIO(image_bytes))
    results = yolo_model(image)
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

def perform_segmentation(image_bytes, bounding_boxes, real_coin_area=13**2 * np.pi):
        CHECKPOINT_PATH = "./data/models/sam_vit_h_4b8939.pth"
        MODEL_TYPE = "vit_h"
        DEVICE = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        sam_model = sam_model_registry[MODEL_TYPE](checkpoint=CHECKPOINT_PATH).to(device=DEVICE)
        mask_predictor = SamPredictor(sam_model)

        # Load and prepare the image
        image = Image.open(io.BytesIO(image_bytes))
        image_np = np.array(image)
        image_rgb = cv2.cvtColor(image_np, cv2.COLOR_BGR2RGB)
        mask_predictor.set_image(image_rgb)

        details = {}
        coin_image_area = None
        confidence = 0

        for obj in bounding_boxes:
            bbox = np.array(obj['bbox'])
            masks, scores, _ = mask_predictor.predict(box=bbox, multimask_output=False)
            area = np.count_nonzero(masks[0])
            details[obj['name']] = {'mask': masks[0], 'image_area': area, 'object_id': obj['class']}
            if obj['name'] == 'coin' and float(obj['confidence'])>confidence:
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

def calculate_volume_and_mass(segmentation_details, depth_map):
        regression_model = joblib.load('./data/models/regression_model.pkl')
        depth_map = np.array(depth_map)
        depth_map *= 100

        results = []
        regression_input = {'object_id': [], 'area': [], 'volume': []}
        names = []
        cam_plane_to_coin = None
        coin_to_plate = 25
        coin_details = segmentation_details.get('coin')
        if coin_details:
            coin_mask = coin_details['mask']
            coin_x_centroid, coin_y_centroid = coin_centroid(coin_mask)
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
            # object = []
            masked_depth_map = np.where(mask, depth_map, 0)
            non_zero_depths = masked_depth_map[masked_depth_map!=0]

            if non_zero_depths.size > 0:
                food_depths = np.maximum(0, cam_plane_to_coin - non_zero_depths)
                food_depths = np.maximum(0, food_depths - coin_to_plate)
                volume = np.sum(food_depths) * details['scale_factor']
                regression_input['volume'].append(volume)
            else:
                regression_input['volume'].append(0)
        
        regression_input = pd.DataFrame.from_dict(regression_input)
        pred = regression_model.predict(regression_input)

        for name, mass in zip(names, pred):
            results.append({'name': name, 'mass': mass})

        return results

def coin_centroid(mask):
        indices = np.argwhere(mask>0)
        if indices.size == 0:
            return None, None
        y_centroid, x_centroid = np.mean(indices, axis=0)
        return int(x_centroid), int(y_centroid)

def calorie_estimation(image_bytes, detected_objects):
    image_bytes = resize_and_save_image(image_bytes)
    segmentation_details = perform_segmentation(image_bytes, detected_objects)
    depth_maps = depth_estimation(image_bytes)
    results = calculate_volume_and_mass(segmentation_details, depth_maps)
    serialized_classes = json.dumps(results)

    return results
