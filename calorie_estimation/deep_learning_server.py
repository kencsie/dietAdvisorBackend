from flask import Flask, request, jsonify
from async_utility import calorie_estimation, resize_and_save_image, yolo_detection
import base64
import json

app = Flask(__name__)

@app.route('/yolo', methods=['POST'])
def upload_file():
    if request.method == 'POST':
        image_data = request.files['image'].read()
        image_data = resize_and_save_image(image_data)
        result = yolo_detection(image_data)
        return jsonify(result)

@app.route('/calorie', methods=['POST'])
def calorie():
    if request.method == 'POST':
        if 'image' in request.files:
            image_file = request.files['image']

            # Process the image directly
            image_data = image_file.read()

        # Check for JSON data in the request form or as a separate part
        if 'data' in request.files:
            detected_objects = json.loads(request.files['data'].read())

            result = calorie_estimation(image_data, detected_objects)

            return jsonify(result)

        return 'Missing image or data', 400

if __name__ == '__main__':
    app.run(debug=False, host='0.0.0.0', port=8081)
