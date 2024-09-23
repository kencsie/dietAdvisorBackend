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
import io

# Global settings
FL = 715.0873
FY = 256 * 0.6
FX = 256 * 0.6
NYU_DATA = False
FINAL_HEIGHT = 640
FINAL_WIDTH = 640
DATASET = 'nyu' # Lets not pick a fight with the model's dataloader

def process_images(model, image_bytes, device):
    try:
        image_stream = io.BytesIO(image_bytes)
        color_image = Image.open(image_stream).convert('RGB')
        original_width, original_height = color_image.size
        image_tensor = transforms.ToTensor()(color_image).unsqueeze(0).to(device)

        pred = model(image_tensor, dataset=DATASET)
        if isinstance(pred, dict):
            pred = pred.get('metric_depth', pred.get('out'))
        elif isinstance(pred, (list, tuple)):
            pred = pred[-1]
        pred = pred.squeeze().detach().cpu().numpy()
        
        resized_pred = Image.fromarray(pred).resize((FINAL_WIDTH, FINAL_HEIGHT), Image.NEAREST)
        return resized_pred
    except Exception as e:
        print(f"Error processing image: {e}")

def depth_estimation(image_bytes):
    model_name = 'zoedepth'
    pretrained_resource = 'local::data/models/Depth-Anything/metric_depth/checkpoints/nutrition5k_03-May_12-04-b56f6cfdfe15_latest.pt'

    config = get_config(model_name, "eval", DATASET)
    config.pretrained_resource = pretrained_resource
    model = build_model(config).to('cuda' if torch.cuda.is_available() else 'cpu')
    model.eval()
    return process_images(model, image_bytes, 'cuda' if torch.cuda.is_available() else 'cpu')
