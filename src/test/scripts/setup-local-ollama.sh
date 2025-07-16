#!/bin/bash

# Local Ollama Setup Script - Native OpenAI API Compatibility
# This script sets up Ollama with native OpenAI-compatible endpoints
# Usage: ./scripts/setup-local-ollama.sh [MODEL_NAME]
# Models: phi3:mini (default), llama3.2:3b, qwen2.5:7b, llama3.1:8b, mistral:7b

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OLLAMA_DIR="$PROJECT_ROOT/.ollama"

# Model selection
MODEL_CHOICE="${1:-${OLLAMA_MODEL_CHOICE:-phi3:mini}}"

# Model configurations (Ollama model names)
get_model_info() {
    local model="$1"
    local field="$2"
    
    case "$model" in
        "tinyllama")
            case "$field" in
                "name") echo "tinyllama" ;;
                "size") echo "~637MB" ;;
                "desc") echo "TinyLlama 1.1B - Ultra-fast for CI/testing" ;;
            esac
            ;;
        "phi3:mini")
            case "$field" in
                "name") echo "phi3:mini" ;;
                "size") echo "~2.3GB" ;;
                "desc") echo "Microsoft Phi-3 Mini - Excellent balance" ;;
            esac
            ;;
        "llama3.2:3b")
            case "$field" in
                "name") echo "llama3.2:3b" ;;
                "size") echo "~2.0GB" ;;
                "desc") echo "Llama 3.2 3B - Good for low RAM" ;;
            esac
            ;;
        "qwen2.5:7b")
            case "$field" in
                "name") echo "qwen2.5:7b" ;;
                "size") echo "~4.1GB" ;;
                "desc") echo "Qwen2.5 7B - Excellent reasoning" ;;
            esac
            ;;
        "llama3.1:8b")
            case "$field" in
                "name") echo "llama3.1:8b" ;;
                "size") echo "~4.6GB" ;;
                "desc") echo "Llama 3.1 8B - High quality" ;;
            esac
            ;;
        "mistral:7b")
            case "$field" in
                "name") echo "mistral:7b" ;;
                "size") echo "~4.1GB" ;;
                "desc") echo "Mistral 7B - Reasoning expert" ;;
            esac
            ;;
        *)
            return 1
            ;;
    esac
}

# Validate model choice
if ! get_model_info "$MODEL_CHOICE" "name" >/dev/null 2>&1; then
    echo "Unknown model: $MODEL_CHOICE"
    echo ""
    echo "Available models:"
    echo "  tinyllama    - TinyLlama 1.1B (~637MB) - Ultra-fast for CI/testing"
    echo "  phi3:mini    - Microsoft Phi-3 Mini (~2.3GB) - Recommended balance"
    echo "  llama3.2:3b - Llama 3.2 3B (~2.0GB) - Good for low RAM"
    echo "  qwen2.5:7b  - Qwen2.5 7B (~4.1GB) - Excellent reasoning"
    echo "  llama3.1:8b - Llama 3.1 8B (~4.6GB) - High quality"
    echo "  mistral:7b  - Mistral 7B (~4.1GB) - Reasoning expert"
    echo ""
    echo "Usage: ./scripts/setup-local-ollama.sh [MODEL_NAME]"
    echo "Example: ./scripts/setup-local-ollama.sh phi3:mini"
    exit 1
fi

MODEL_NAME="$(get_model_info "$MODEL_CHOICE" "name")"
MODEL_SIZE="$(get_model_info "$MODEL_CHOICE" "size")"
MODEL_DESC="$(get_model_info "$MODEL_CHOICE" "desc")"

echo "=== Setting up Local Ollama Environment ==="
echo "Model: $MODEL_CHOICE ($MODEL_DESC)"
echo "Size: $MODEL_SIZE"
echo "Using Ollama's native OpenAI API compatibility!"
echo ""

# Create Ollama directory
mkdir -p "$OLLAMA_DIR"
cd "$OLLAMA_DIR"

# Check if Ollama is installed and version
MINIMUM_VERSION="0.1.14"

if ! command -v ollama &> /dev/null; then
    echo "Installing Ollama..."
    curl -fsSL https://ollama.com/install.sh | sh
    echo "Ollama installed successfully"
else
    echo "Ollama already installed"
    
    # Check Ollama version for OpenAI API compatibility
    echo "Checking Ollama version for OpenAI API compatibility..."
    OLLAMA_VERSION=$(ollama --version 2>/dev/null | grep -oE "v?[0-9]+\.[0-9]+\.[0-9]+" | head -1 | sed 's/^v//')
    
    if [ -z "$OLLAMA_VERSION" ]; then
        echo "Could not determine Ollama version"
        echo "   Proceeding with setup, but OpenAI API may not work if version < $MINIMUM_VERSION"
    else
        echo "Detected Ollama version: $OLLAMA_VERSION"
        
        # Simple version comparison (works for most semantic versions)
        if printf '%s\n%s\n' "$MINIMUM_VERSION" "$OLLAMA_VERSION" | sort -V | head -1 | grep -q "^$MINIMUM_VERSION$"; then
            echo "Ollama $OLLAMA_VERSION supports native OpenAI API compatibility"
        else
            echo "Ollama $OLLAMA_VERSION is too old for native OpenAI API compatibility"
            echo ""
            echo "Required: >= $MINIMUM_VERSION (native OpenAI API support)"
            echo "Current:  $OLLAMA_VERSION"
            echo ""
            echo "Please update Ollama:"
            echo "  curl -fsSL https://ollama.com/install.sh | sh"
            echo ""
            echo "Or manually:"
            echo "  brew upgrade ollama        # macOS with Homebrew"
            echo "  sudo apt update && sudo apt upgrade ollama  # Ubuntu/Debian"
            echo ""
            exit 1
        fi
    fi
fi

# Check if Ollama service is already running
if curl -s http://localhost:11434/api/version > /dev/null 2>&1; then
    echo "Ollama service already running"
else
    echo "Starting Ollama service..."
    ollama serve &
    OLLAMA_PID=$!
    echo $OLLAMA_PID > "$OLLAMA_DIR/ollama.pid"
    
    # Wait for Ollama to start
    echo "Waiting for Ollama to start..."
    for i in {1..30}; do
        if curl -s http://localhost:11434/api/version > /dev/null 2>&1; then
            echo "Ollama service started successfully"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "Ollama failed to start within 1 minute"
            exit 1
        fi
        echo "  Attempt $i/30: Still waiting..."
        sleep 2
    done
fi

# Check if model is already available
if ollama list | grep -q "$MODEL_NAME"; then
    echo "Model $MODEL_NAME already available"
else
    echo "Pulling model $MODEL_NAME ($MODEL_SIZE)..."
    echo "This may take several minutes depending on your internet connection..."
    ollama pull "$MODEL_NAME"
    echo "Model $MODEL_NAME pulled successfully"
fi

# Verify model is available
echo ""
echo "Available models:"
ollama list

# Test Ollama's native OpenAI API
echo ""
echo "Testing Ollama's native OpenAI API..."
curl -s -X POST http://localhost:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"$MODEL_NAME\",
    \"messages\": [{\"role\": \"user\", \"content\": \"Hello! Test OpenAI API compatibility.\"}],
    \"max_tokens\": 50
  }" | jq -r '.choices[0].message.content' 2>/dev/null || echo "OpenAI API test completed"

echo ""
echo "Testing models endpoint..."
curl -s http://localhost:11434/v1/models | jq -r '.data[0].id' 2>/dev/null || echo "Models endpoint test completed"

# Create environment configuration for easy access
cat > "$OLLAMA_DIR/config.env" <<EOF
# Ollama Configuration
export OLLAMA_MODEL_NAME="$MODEL_NAME"
export OLLAMA_API_BASE="http://localhost:11434/v1"
export OLLAMA_API_KEY="dummy"  # Not required but some clients expect it

# For ML-Commons connector
export ML_COMMONS_ENDPOINT="http://localhost:11434/v1/chat/completions"
export ML_COMMONS_MODEL="$MODEL_NAME"

# Source this file to set environment variables:
# source .ollama/config.env
EOF

echo ""
echo "Local Ollama Environment Ready!"
echo ""
echo "Native OpenAI-Compatible Endpoints:"
echo "  Chat Completions: http://localhost:11434/v1/chat/completions"
echo "  Models List:      http://localhost:11434/v1/models"
echo "  Embeddings:       http://localhost:11434/v1/embeddings"
echo ""
echo "Model Information:"
echo "  Name: $MODEL_NAME"
echo "  Description: $MODEL_DESC"
echo "  Size: $MODEL_SIZE"
echo ""
echo "Configuration:"
echo "  Environment: .ollama/config.env"
if [ -f "$OLLAMA_DIR/ollama.pid" ]; then
    echo "  Ollama PID: $(cat $OLLAMA_DIR/ollama.pid)"
fi
echo ""
echo "Quick Test Commands:"
echo "  # Test chat completion"
echo "  curl http://localhost:11434/v1/chat/completions \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"model\":\"$MODEL_NAME\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello!\"}]}'"
echo ""
echo "  # List available models"
echo "  curl http://localhost:11434/v1/models"
echo ""
echo "Next Steps:"
echo "  1. Source config: source .ollama/config.env"
echo "  2. Run tests: ./gradlew integTest --tests '*LLMJudgmentGenerationIT*'"
echo "  3. Stop service: ./scripts/stop-local-ollama.sh"
echo ""
