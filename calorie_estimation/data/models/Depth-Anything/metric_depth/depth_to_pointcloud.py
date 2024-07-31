# Born out of Issue 36. 
# Allows  the user to set up own test files to infer on (Create a folder my_test and add subfolder input and output in the metric_depth directory before running this script.)
# Make sure you have the necessary libraries
# Code by @1ssb

import argparse
import os
import glob
import torch
import numpy as np
from PIL import Image
import torchvision.transforms as transforms
import open3d as o3d
from tqdm import tqdm
from zoedepth.models.builder import build_model
from zoedepth.utils.config import get_config

# Global settings
FL = 715.0873
FY = 256 * 0.6
FX = 256 * 0.6
NYU_DATA = False
FINAL_HEIGHT = 640
FINAL_WIDTH = 640
INPUT_DIR = '../../../../static'
OUTPUT_DIR = '../../../depth_maps'
DATASET = 'nyu' # Lets not pick a fight with the model's dataloader

def process_images(model):
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    image_paths = glob.glob(os.path.join(INPUT_DIR, '*.png')) + glob.glob(os.path.join(INPUT_DIR, '*.jpg')) + glob.glob(os.path.join(INPUT_DIR, '*.jpeg'))
    for image_path in tqdm(image_paths, desc="Processing Images"):
        try:
            color_image = Image.open(image_path).convert('RGB')
            original_width, original_height = color_image.size
            image_tensor = transforms.ToTensor()(color_image).unsqueeze(0).to('cuda' if torch.cuda.is_available() else 'cpu')

            pred = model(image_tensor, dataset=DATASET)
            if isinstance(pred, dict):
                pred = pred.get('metric_depth', pred.get('out'))
            elif isinstance(pred, (list, tuple)):
                pred = pred[-1]
            pred = pred.squeeze().detach().cpu().numpy()
            

            resized_pred = Image.fromarray(pred).resize((FINAL_WIDTH, FINAL_HEIGHT), Image.NEAREST)

            # Save the depth map directly into a .npy file
            npy_file_path = os.path.join(OUTPUT_DIR, os.path.splitext(os.path.basename(image_path))[0] + ".npy")
            np.save(npy_file_path, resized_pred)
        except Exception as e:
            print(f"Error processing {image_path}: {e}")

def main(model_name, pretrained_resource):
    config = get_config(model_name, "eval", DATASET)
    config.pretrained_resource = pretrained_resource
    model = build_model(config).to('cuda' if torch.cuda.is_available() else 'cpu')
    model.eval()
    process_images(model)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--model", type=str, default='zoedepth', help="Name of the model to test")
    parser.add_argument("-p", "--pretrained_resource", type=str, default='local::./checkpoints/nutrition5k_03-May_12-04-b56f6cfdfe15_latest.pt', help="Pretrained resource to use for fetching weights.")
    args = parser.parse_args()
    main(args.model, args.pretrained_resource)
