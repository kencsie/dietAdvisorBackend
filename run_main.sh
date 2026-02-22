export OPENROUTER_BEARER_TOKEN=your_openrouter_token
export MONGO_HOST=your_mongo_host
export MONGO_PORT=your_mongo_port
export FLASK_HOST=your_flask_host
export FLASK_PORT=your_flask_port
export URL_PROVIDER=your_url
export GOOGLE_CLIENT_ID=your_google_client_id
export GOOGLE_CLIENT_SECRET=your_google_client_secret

apt-get update
apt-get install default-jre -y
./gradlew run
