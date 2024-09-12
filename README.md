# DietAdvisor Backend Server

## Introduction
This backend server is designed to integrate with the DietAdvisor app, providing key functionalities such as nutrition estimation and personalized dietary recommendations. Its purpose is to support the app in delivering accurate and helpful nutritional insights based on user data and preferences.

## Prerequisites
Before you begin, ensure you meet the following requirements:
- **Graphics Card:** Minimum 6GB of VRAM.
- **MongoDB:** This project uses MongoDB for database management.
- **OpenRouter:** You will need an OpenRouter key to manage routing.
- **Domain:** A custom domain is required for proper deployment.

## Installation
Follow these steps to get your development environment running:

1. **Clone the repository**
   ```bash
   git clone [repository-url]
   cd [repository-directory]
   ```

2. **Set up environment variables**
 Create a .env file in the project directory and populate it with the following variables. Then, use the export command in your shell to set these variables for your session:
   ```bash
   export OPENROUTER_BEARER_TOKEN=your_openrouter_bearer_token
   export MONGO_HOST=your_mongodb_host
   export MONGO_PORT=your_mongodb_port
   export FLASK_HOST=your_flask_host
   export FLASK_PORT=your_flask_port
   export URL_PROVIDER=your_url_provider
   ```

Alternatively, you can directly export these variables in your shell session if you prefer not to use a .env file.

3. **Start your application**
   ```bash
   ./gradlew run
   python calorie_estimation/deep_learning_server.py
   ```