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
import torchvision.transforms as transforms
from segment_anything import sam_model_registry, SamPredictor
import time
import concurrent.futures

# # Global model loading
# yolo_model = torch.hub.load('ultralytics/yolov5', 'custom', path='./website/data/models/yolo.pt')
# yolo_model.eval()

# sam_model_details = sam_model_registry["vit_h"]
# sam_model = sam_model_details(checkpoint="./website/data/models/sam_vit_h_4b8939.pth").to(torch.device('cuda' if torch.cuda.is_available() else 'cpu'))

# regression_model = joblib.load('./website/data/models/regression_model.pkl')

def upload_file():
    def resize_and_save_image(image_path):
        with Image.open(image_path) as img:
            img = img.resize((640, 640), 1)
            img.save(image_path)

    def yolo_object_detection(image_path, output_path):
        yolo_model = torch.hub.load('ultralytics/yolov5', 'custom', path=f'{os.getcwd()}/data/models/yolo.pt')
        yolo_model.eval()
        results = yolo_model(image_path)
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

        # filename = os.path.basename(image_path)
        results.save(save_dir=output_path, exist_ok=True)

        # return detected_classes
        return detected_objects

    def perform_segmentation(image_path, bounding_boxes, real_coin_area):
        CHECKPOINT_PATH = f"/config/workspace/dietAdvisorBackend/calorie_estimation/data/models/sam_vit_h_4b8939.pth"
        MODEL_TYPE = "vit_h"
        DEVICE = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        sam_model = sam_model_registry[MODEL_TYPE](checkpoint=CHECKPOINT_PATH).to(device=DEVICE)
        mask_predictor = SamPredictor(sam_model)

        # Load and prepare the image
        image = cv2.imread(image_path)
        image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
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

    def estimate_depth(image_path):
        torch.cuda.empty_cache()
        depth_map_directory = './data/depth_maps'
        depth_map_path = os.path.join(depth_map_directory, os.path.splitext(os.path.basename(image_path))[0] + '.npy')
        os.makedirs(depth_map_directory, exist_ok=True)

        # Change to the script's directory
        c = os.getcwd()
        os.chdir('./data/models/Depth-Anything/metric_depth')

        try:
            result = subprocess.run(
                ['python', './depth_to_pointcloud.py'],
                check=True, text=True, capture_output=True)
            print("Output:", result.stdout)
        except subprocess.CalledProcessError as e:
            print("Error:", e.stderr)
            raise e
        finally:
            # Change back to the original directory
            os.chdir(c) 

        return depth_map_path

    def coin_centroid(mask):
        indices = np.argwhere(mask>0)
        if indices.size == 0:
            return None, None
        y_centroid, x_centroid = np.mean(indices, axis=0)
        return int(x_centroid), int(y_centroid)

    def calculate_volume_and_mass(segmentation_details, depth_map_path):
        regression_model = joblib.load('./data/models/regression_model.pkl')
        depth_map = np.load(depth_map_path)
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

    def detection_and_segmentation(file_path, output_folder):
        detected_objects = yolo_object_detection(file_path, output_folder)
        coin_real = 13**2 * np.pi
        return perform_segmentation(file_path, detected_objects, real_coin_area=coin_real)


    # Define the path to save processed images
    output_folder = os.path.join(os.getcwd(), 'static', 'results')
    os.makedirs(output_folder, exist_ok=True)

    file_path = os.path.join(os.getcwd(), 'static', 'IMG_9265.jpeg')

    # Resize and save image
    resize_and_save_image(file_path)

    # Execute both tasks concurrently
    with concurrent.futures.ThreadPoolExecutor() as executor:
        # Schedule the tasks
        future_segmentation = executor.submit(detection_and_segmentation, file_path, output_folder)
        future_depth = executor.submit(estimate_depth, file_path)

        # Retrieve results when both are ready
        segmentation_details = future_segmentation.result()
        depth_map_path = future_depth.result()

    # Calculate volume and mass after both tasks have finished
    results = calculate_volume_and_mass(segmentation_details, depth_map_path)

    #os.remove(file_path)

    # Serialize the detected classes to JSON to be sent through the query parameters
    serialized_classes = json.dumps(results)

    # Encode data to be sent through query parameters
    encoded_classes = quote(serialized_classes)
    print(serialized_classes)
    results_image_path = os.path.join(output_folder,  os.path.splitext(os.path.basename(file_path))[0] + '.jpg')
    
    #return redirect(url_for('views.results', detected_classes=encoded_classes, image_name=os.path.basename(results_image_path)))

start_time = time.time()  # Start time
upload_file()
end_time = time.time()  # End time
print(f"Execution time: {end_time - start_time} seconds")