# DietAdvisor Backend Server

A backend server for the DietAdvisor app, providing AI-powered food detection, calorie estimation, and personalized dietary recommendations. Built with **Ktor (Kotlin)** for the API server and **Flask (Python)** for the deep learning inference pipeline.

## Architecture

```
Client App
    │
    ▼
┌──────────────┐       ┌─────────────────────┐
│  Ktor Server │──────▶│  Flask DL Server    │
│  (Port 5000) │       │  (Port 8081)        │
│              │       │                     │
│  - Auth      │       │  - YOLOv5 Detection │
│  - User CRUD │       │  - SAM Segmentation │
│  - API       │       │  - Depth Anything   │
│  - LLM Reco  │       │  - Calorie Estimate │
└──────┬───────┘       └─────────────────────┘
       │
       ▼
┌──────────────┐       ┌──────────────┐
│   MongoDB    │       │  OpenRouter  │
│  (User Data) │       │  (GPT-4o)   │
└──────────────┘       └──────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| API Server | Kotlin 2.0.10 + Ktor 2.3.12 |
| DL Server | Python 3.11 + Flask 3.1 |
| Database | MongoDB 4.9.0 |
| Authentication | OAuth2 (Google) |
| LLM | OpenRouter (GPT-4o mini) |
| Build Tool | Gradle (Kotlin DSL) |
| Python Package Manager | uv |
| API Docs | OpenAPI 3.0.3 + Swagger UI |

## Features

- **Google OAuth2 Login** - Secure user authentication via Google accounts
- **User Management** - CRUD operations for user profiles, body measurements, and dietary goals
- **Food Detection** - YOLOv5-based food item recognition from images
- **Calorie Estimation** - Multi-model pipeline using SAM segmentation + Depth Anything depth estimation + regression model to estimate mass and macros from food photos
- **Meal Recommendations** - Personalized dinner suggestions via LLM, considering user's TDEE, intake history, and dietary goals
- **Multi-language Support** - English and Chinese prompt templates

## Prerequisites

- **JDK 8+**
- **MongoDB** installed and running
- **Python 3.11+**
- **uv** (Python package manager)
- **NVIDIA GPU** with 6GB+ VRAM (for deep learning models)
- **OpenRouter API key**
- **Google OAuth2 credentials** (Client ID & Secret)

## Project Structure

```
dietAdvisorBackend/
├── src/main/kotlin/example/com/
│   ├── Application.kt              # Entry point, CORS config
│   ├── plugins/
│   │   ├── Routing.kt              # API endpoints (/yolo, /calorie, /recommendation)
│   │   ├── Security.kt             # Google OAuth2 auth & token validation
│   │   ├── Databases.kt            # MongoDB connection & user CRUD routes
│   │   ├── UsersSchema.kt          # UserService (MongoDB operations)
│   │   ├── Session.kt              # Cookie session management
│   │   ├── Serialization.kt        # JSON serialization
│   │   └── HTTP.kt                 # Swagger UI setup
│   ├── model/
│   │   ├── User.kt                 # User data models & enums
│   │   └── Chat.kt                 # LLM request/response models & prompt builder
│   └── static/
│       ├── prompt_template_en.txt  # English LLM prompt template
│       └── prompt_template_zh.txt  # Chinese LLM prompt template
├── calorie_estimation/
│   ├── deep_learning_server.py     # Flask DL inference server (entry point)
│   ├── async_utility.py            # Async multi-GPU model manager & inference
│   ├── utility.py                  # Single-GPU inference utilities
│   └── data/
│       ├── nutrient_data.json      # Nutritional database (22 food items)
│       ├── prompt_template.txt     # DL server prompt template
│       └── models/                 # Pre-trained model weights (not tracked by git)
│           ├── yolo.pt
│           ├── regression_model.pkl
│           ├── sam_vit_h_4b8939.pth
│           └── Depth-Anything/
│               └── metric_depth/
│                   └── checkpoints/
│                       ├── depth_anything_vitl14.pth
│                       └── nutrition5k_03-May_12-04-b56f6cfdfe15_latest.pt
├── build.gradle.kts
├── gradle.properties
├── pyproject.toml
├── run_main.sh                     # Script to start the Ktor server
└── run_calorie.sh                  # Script to start the Flask DL server
```

## Installation & Running

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd dietAdvisorBackend
   ```

2. **Download model weights**

   The following 5 model files are required and must be placed manually (excluded from git via `.gitignore`):

   | # | File | Path | Description |
   |---|------|------|-------------|
   | 1 | `yolo.pt` | `calorie_estimation/data/models/yolo.pt` | YOLOv5 food object detection |
   | 2 | `sam_vit_h_4b8939.pth` | `calorie_estimation/data/models/sam_vit_h_4b8939.pth` | SAM ViT-H segmentation model |
   | 3 | `regression_model.pkl` | `calorie_estimation/data/models/regression_model.pkl` | Mass/calorie regression model |
   | 4 | `depth_anything_vitl14.pth` | `calorie_estimation/data/models/Depth-Anything/metric_depth/checkpoints/depth_anything_vitl14.pth` | Depth Anything ViT-L/14 backbone |
   | 5 | `nutrition5k_03-May_12-04-b56f6cfdfe15_latest.pt` | `calorie_estimation/data/models/Depth-Anything/metric_depth/checkpoints/nutrition5k_03-May_12-04-b56f6cfdfe15_latest.pt` | Custom ZoeDepth checkpoint (fine-tuned on Nutrition5k) |

3. **Install and start MongoDB**
   ```bash
   # Ubuntu/Debian
   sudo apt-get install -y mongodb && sudo systemctl start mongodb

   # macOS (via Homebrew)
   brew install mongodb-community && brew services start mongodb-community
   ```

4. **Fill in credentials in `run_main.sh`**

   Edit `run_main.sh` and replace the placeholder values:
   ```bash
   export OPENROUTER_BEARER_TOKEN=your_openrouter_token
   export MONGO_HOST=your_mongo_host
   export MONGO_PORT=your_mongo_port
   export FLASK_HOST=your_flask_host
   export FLASK_PORT=your_flask_port
   export URL_PROVIDER=your_url
   export GOOGLE_CLIENT_ID=your_google_client_id
   export GOOGLE_CLIENT_SECRET=your_google_client_secret
   ```

4. **Start both servers** (each in a separate terminal)
   ```bash
   # Terminal 1 — Ktor API server (port 5000)
   ./run_main.sh

   # Terminal 2 — Flask deep learning server (port 8081)
   ./run_calorie.sh
   ```

Swagger UI is available at: `http://<your-host>:5000/swagger`

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/login` | Initiate Google OAuth2 login |
| GET | `/callback` | OAuth2 callback handler |
| GET | `/login/complete` | Display access token after login |

### User Management (requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description | Status Codes |
|--------|----------|-------------|--------------|
| POST | `/user` | Create new user | 201, 409, 400 |
| GET | `/user` | Get user data | 200, 404, 400 |
| PUT | `/user` | Update user info | 200, 404, 400 |
| DELETE | `/user` | Delete user account | 200, 404, 400 |

### AI Features

| Method | Endpoint | Description | Content-Type |
|--------|----------|-------------|--------------|
| POST | `/yolo` | Detect food items in image | `multipart/form-data` |
| POST | `/calorie` | Estimate calories from food image | `multipart/form-data` |
| POST | `/recommendation` | Generate dinner meal recommendation | `application/json` |

### Example: Create User

```bash
curl -X POST http://localhost:5000/user \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "personalInfo": {
      "userID": "",
      "userName": "",
      "birthDate": "1990-01-15",
      "gender": "Male",
      "language": "English"
    },
    "bodyMeasurements": {
      "weight": 75.5,
      "height": 180.0,
      "physicalActivity": 1.5
    },
    "dietaryInfo": {
      "dietaryGoal": "Lose weight",
      "dietaryGoalAmount": 500.0,
      "TMR": 1800.0,
      "TDEE": 2300.0
    },
    "intakeHistory": [],
    "lastMeal": null
  }'
```

### Example: Food Detection

```bash
curl -X POST http://localhost:5000/yolo \
  -F "image=@food.png"
```

### Example: Calorie Estimation

```bash
curl -X POST http://localhost:5000/calorie \
  -F "image=@food.png" \
  -F 'data=[{"class": 0, "name": "rice", "confidence": 0.92, "bbox": [10, 20, 200, 300]}]'
```

## Data Models

### OAuthUser

```json
{
  "personalInfo": {
    "userID": "string (set from Google OAuth)",
    "userName": "string (set from Google OAuth)",
    "birthDate": "YYYY-MM-DD",
    "gender": "Male | Female | Non-binary",
    "language": "English | Chinese"
  },
  "bodyMeasurements": {
    "weight": 75.5,
    "height": 180.0,
    "physicalActivity": 1.5
  },
  "dietaryInfo": {
    "dietaryGoal": "Gain weight | Maintain weight | Lose weight",
    "dietaryGoalAmount": 500.0,
    "TMR": 1800.0,
    "TDEE": 2300.0
  },
  "intakeHistory": [
    {
      "date": "YYYY-MM-DD",
      "nutritionalInfo": {
        "carb": 200.0,
        "protein": 80.0,
        "fat": 60.0,
        "calorie": 1640.0
      }
    }
  ],
  "lastMeal": {
    "carb": 80.0,
    "protein": 30.0,
    "fat": 20.0,
    "calorie": 620.0
  }
}
```

## Deep Learning Pipeline

The calorie estimation pipeline processes food images through multiple stages:

```
Input Image
     │
     ▼
1. YOLOv5 ──────────── Detects & localizes food items + coin
     │
     ▼
2. SAM (ViT-H) ──────── Segments each detected item for precise pixel area
     │
     ▼
3. Depth Anything ───── Estimates depth map (ViT-L/14 backbone,
     │                   fine-tuned on Nutrition5k dataset via ZoeDepth framework)
     │
     ▼
4. Coin Calibration ─── Uses detected coin as real-world size reference
     │                   to convert pixel area → real area (cm²)
     │
     ▼
5. Regression Model ─── Maps (food type, area, volume) → mass (g)
     │
     ▼
Output: mass per food item (used to look up macros from nutrient_data.json)
```

### Supported Food Items (22 items)

Kabayaki sea bream fillet, Spam, Apple, Cabbage, Creamy tofu, Creamy tofu (without sauce), Cucumber, Egg tofu, Firm tofu, Fish cake, Fried chicken cutlet, Fried potato, Grilled pork, Guava, Mustard greens, Pig blood curd, Pig liver, Pineapple, Pumpkin, Red grilled pork, Soy egg, Sweet potato leaves, Rice

> YOLO also detects **Coin** as a calibration reference for real-world size estimation, but it is not a food item.

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `OPENROUTER_BEARER_TOKEN` | OpenRouter API key for LLM | Yes |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID | Yes |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret | Yes |
| `URL_PROVIDER` | Domain used for OAuth callback URL | Yes |
| `MONGO_HOST` | MongoDB host address | Yes |
| `MONGO_PORT` | MongoDB port | Yes |
| `FLASK_HOST` | Flask DL server host | Yes |
| `FLASK_PORT` | Flask DL server port | Yes |
