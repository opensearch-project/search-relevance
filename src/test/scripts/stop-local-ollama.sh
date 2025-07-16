#!/bin/bash

# Local Ollama Stop Script - Native OpenAI API Compatible
# This script stops the local Ollama service
# Usage: ./scripts/stop-local-ollama.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OLLAMA_DIR="$PROJECT_ROOT/.ollama"

echo "=== Stopping Local Ollama Environment ==="
echo ""

# Stop Ollama service (if we started it)
if [ -f "$OLLAMA_DIR/ollama.pid" ]; then
    OLLAMA_PID=$(cat "$OLLAMA_DIR/ollama.pid")
    if kill -0 $OLLAMA_PID 2>/dev/null; then
        echo "Stopping Ollama service (PID: $OLLAMA_PID)..."
        kill $OLLAMA_PID
        rm "$OLLAMA_DIR/ollama.pid"
        echo "Ollama service stopped"
    else
        echo "Ollama PID file exists but process not running"
        rm "$OLLAMA_DIR/ollama.pid"
    fi
else
    echo "No Ollama PID file found"
    echo "Checking if Ollama is running..."
    
    # Check if Ollama is running and try to stop it
    if curl -s http://localhost:11434/api/version > /dev/null 2>&1; then
        echo "Ollama is running - attempting to stop..."
        
        # First try to stop any Ollama processes
        OLLAMA_PROCS=$(pgrep -f "ollama serve" 2>/dev/null || true)
        if [ ! -z "$OLLAMA_PROCS" ]; then
            echo "Found Ollama processes: $OLLAMA_PROCS"
            echo "Stopping Ollama processes..."
            pkill -f "ollama serve" || true
            
            # Wait a moment for graceful shutdown
            sleep 3
            
            # Check if still running
            if curl -s http://localhost:11434/api/version > /dev/null 2>&1; then
                echo "Ollama still running, forcing termination..."
                pkill -9 -f "ollama serve" 2>/dev/null || true
                sleep 2
            fi
        fi
        
        # Try stopping via systemctl if it appears to be a system service
        if curl -s http://localhost:11434/api/version > /dev/null 2>&1; then
            if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet ollama 2>/dev/null; then
                echo "Attempting to stop system Ollama service..."
                sudo systemctl stop ollama 2>/dev/null || echo "Could not stop system service (may require sudo)"
            fi
        fi
    else
        echo "Ollama is not running"
    fi
fi

# Final cleanup - make sure all Ollama processes are gone
REMAINING_PROCS=$(pgrep -f "ollama" 2>/dev/null || true)
if [ ! -z "$REMAINING_PROCS" ]; then
    echo ""
    echo "Found remaining Ollama processes: $REMAINING_PROCS"
    echo "Performing final cleanup..."
    pkill -9 -f "ollama" 2>/dev/null || true
    sleep 1
fi

# Check final status
echo ""
echo "Final status check:"

if curl -s http://localhost:11434/api/version > /dev/null 2>&1; then
    echo "  Ollama API: Still running on port 11434"
    echo "  Native OpenAI endpoints: http://localhost:11434/v1/*"
else
    echo "  Ollama API: Stopped (port 11434 free)"
fi

echo ""
echo "Local Ollama Environment Cleanup Complete!"
echo ""
echo "What was cleaned up:"
echo "  - Local Ollama processes (if started by setup script)"
echo "  - PID files"
echo ""
echo "To restart: ./scripts/setup-local-ollama.sh [model]"
echo "Available models: tinyllama, phi3:mini, llama3.2:3b, etc."
echo ""
