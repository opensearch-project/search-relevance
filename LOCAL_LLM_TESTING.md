# Local LLM Testing Setup

This guide shows you how to test LLM integration locally using Ollama instead of relying on GitHub CI, making development much more efficient and consistent with CI testing.

## Quick Start

### 1. Setup Local Ollama Environment
```bash
# Start with recommended balanced model
./src/test/scripts/setup-local-ollama.sh phi3:mini     # 2.3GB, great balance (default)

# OR choose other models:
./src/test/scripts/setup-local-ollama.sh llama3.2:3b  # 2.0GB, good for low RAM
./src/test/scripts/setup-local-ollama.sh qwen2.5:7b   # 4.1GB, excellent reasoning
./src/test/scripts/setup-local-ollama.sh llama3.1:8b  # 4.6GB, high quality
./src/test/scripts/setup-local-ollama.sh mistral:7b   # 4.1GB, reasoning expert
```

### 2. Run LLM Tests

**Option A: Using the test script (recommended)**
```bash
# Runs the full LLM test suite with proper environment setup
./src/test/scripts/test-ollama-native.sh
```

**Option B: Direct Gradle command**
```bash
# Run specific LLM integration test directly
./gradlew integTest --tests "*LLMJudgmentGenerationIT" -Dtests.cluster.llm.enabled=true

# Or run all integration tests with LLM enabled
./gradlew integTest -Dtests.cluster.llm.enabled=true
```

**Note**: The test requires `-Dtests.cluster.llm.enabled=true` to run. Without this flag, the test will be skipped.

### 3. Stop Ollama Services
```bash
# Clean shutdown
./src/test/scripts/stop-local-ollama.sh
```

## What Gets Created

The setup script creates a `.ollama/` directory in your project root:

```
search-relevance/
├── .ollama/                        # Local LLM files (add to .gitignore)
│   ├── ollama.log                # Server logs
│   └── ollama.pid                # Process ID
└── src/test/scripts/
    ├── setup-local-ollama.sh      # Setup & start LLM
    ├── test-ollama-native.sh      # Run tests
    └── stop-local-ollama.sh       # Stop LLM
```

## Technical Details

### Server Configuration
- **URL**: `http://localhost:11434`
- **Model**: `phi3:mini` (default, configurable)
- **API**: OpenAI-compatible endpoints
- **Size**: ~2.3GB download (for phi3:mini)
- **Startup**: 30-60 seconds

### Test Environment
The test script sets these environment variables:
```bash
export OLLAMA_API_BASE="http://localhost:11434/v1"
export OLLAMA_MODEL_NAME="phi3:mini"
```

### Integration Test
Runs: `LLMJudgmentGenerationIT` with:
- ML Commons connector setup
- LLM model registration & deployment
- Document indexing with test data
- LLM judgment generation
- Verification of relevance ratings

## Troubleshooting

### LLM Server Won't Start
```bash
# Check if port 11434 is busy
lsof -ti:11434

# View server logs
tail -f .ollama/ollama.log

# Force kill any ollama processes
pkill -f "ollama serve"
```

### Test Failures
1. **Check LLM server is running**:
   ```bash
   curl http://localhost:11434/v1/models
   ```

2. **Verify API response**:
   ```bash
   curl -X POST http://localhost:11434/v1/chat/completions \
     -H "Content-Type: application/json" \
     -d '{"model": "phi3:mini", "messages": [{"role": "user", "content": "Hello"}], "max_tokens": 10}'
   ```

3. **Check OpenSearch logs**:
   ```bash
   find build/testclusters -name "*.log" -exec tail -f {} +
   ```

### Port Conflicts
If port 11434 is busy, you can change it by editing the scripts:
- Update port configuration in Ollama service
- Update `OLLAMA_API_BASE` in test script

## Development Workflow

### Efficient Testing Loop
1. **Start LLM once**: `./src/test/scripts/setup-local-ollama.sh`
2. **Iterate on code**: Make changes to your LLM integration
3. **Test quickly**: `./src/test/scripts/test-ollama-native.sh` (reuses running LLM)
4. **Debug locally**: Check logs, API responses
5. **Stop when done**: `./src/test/scripts/stop-local-ollama.sh`

### Before Pushing to GitHub
1. Test locally first with these scripts
2. Only push to GitHub CI when local tests pass
3. GitHub CI will run the same Phi-3 Mini model

## Performance Comparison

| Method | Time | Bandwidth | Iterations |
|--------|------|-----------|------------|
| **Local Testing** | ~5 min | ~2.3GB once | Fast iteration |
| **GitHub CI Push** | ~8 min | Full repo each time | Slow iteration |

## Benefits

- **Fast Iteration**: Test changes in minutes, not CI queue time
- **Better Debugging**: Direct access to logs and API responses
- **Resource Efficient**: No GitHub Actions minutes consumed
- **Parallel Development**: Multiple developers can test independently
- **Offline Capable**: Works without internet after initial download

## Integration with GitHub CI

The local scripts use **exactly the same model and configuration** as GitHub CI:
- Same Phi-3 Mini model
- Same OpenAI-compatible API
- Same environment variables
- Same test execution

This ensures **local results match CI results**!

## Adding to .gitignore

Add this to your `.gitignore` to avoid committing LLM files:
```
# Local LLM files
.ollama/
```

## Need Help?

1. **Check the logs**: `tail -f .ollama/ollama.log`
2. **Verify connectivity**: `curl http://localhost:11434/v1/models`
3. **Clean restart**: `./src/test/scripts/stop-local-ollama.sh && ./src/test/scripts/setup-local-ollama.sh`
4. **Check system resources**: LLM needs ~2-4GB RAM (depending on model)

---

**Happy LLM Testing!**
