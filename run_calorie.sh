apt-get update && apt-get install curl -y
curl -LsSf https://astral.sh/uv/install.sh | sh
source $HOME/.local/bin/env
uv run calorie_estimation/deep_learning_server.py

