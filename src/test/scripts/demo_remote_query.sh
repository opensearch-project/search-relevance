#!/bin/bash

#
# Copyright OpenSearch Contributors
# SPDX-License-Identifier: Apache-2.0
#

# Enhanced Remote Query Experiment Demonstration Script
#
# This script demonstrates the complete integration of remote search with the Search Relevance Workbench
# experiment framework. It creates an experiment that compares local OpenSearch results with remote Solr
# results using standard Information Retrieval metrics.
#
# The script:
# 1. Sets up identical data in OpenSearch and Solr
# 2. Creates local and remote search configurations
# 3. Creates a query set and judgments from ESCI data
# 4. Runs a PAIRWISE_COMPARISON experiment between local and remote configurations
# 5. Shows IR metrics comparison (NDCG, MAP, MRR, Precision@K)
# 6. Demonstrates query template transformation and response mapping
# 7. Executes search comparisons between local and remote systems
#
# This script executes live remote queries via Search Relevance plugin endpoints
# (/_plugins/_search_relevance/remote_search_configurations and /remote_search/execute)
# against a running Solr instance and demonstrates complete experiment integration.

set -o pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
OPENSEARCH_URL="http://localhost:9200"
SOLR_URL="http://localhost:8983"
SOLR_CORE="ecommerce"
ECOMMERCE_DATA_FILE="esci_us_opensearch-2025-06-06.json"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Experiment configuration
LOCAL_CONFIG_NAME="opensearch_local"
REMOTE_CONFIG_NAME="solr_remote"
QUERY_SET_NAME="esci_demo_queries"
JUDGMENT_SET_NAME="esci_demo_judgments"
EXPERIMENT_NAME="local_vs_remote_comparison"

# Command-line options
SKIP_CLEANUP=false
SKIP_DATA_LOADING=false

# Global variables for cleanup tracking
SOLR_CONTAINER_STARTED=false
TEMP_FILES=()
CREATED_RESOURCES=()

# Parse command-line arguments
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-cleanup)
                SKIP_CLEANUP=true
                log_info "Skip cleanup mode enabled - Solr will remain running and data will be preserved"
                shift
                ;;
            --skip-data-loading)
                SKIP_DATA_LOADING=true
                SKIP_CLEANUP=true  # If skipping data loading, also skip cleanup
                log_info "Skip data loading mode enabled - will skip data setup and cleanup steps"
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

# Show usage information
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Enhanced Remote Query Experiment Demonstration Script"
    echo ""
    echo "Options:"
    echo "  --no-cleanup           Skip cleanup - leave all resources for inspection"
    echo "  --skip-data-loading    Skip data loading and setup steps"
    echo "  -h, --help            Show this help message"
    echo ""
    echo "This script demonstrates the complete integration of remote search with"
    echo "the Search Relevance Workbench experiment framework."
}

# Logging functions
log_info() {
    >&2 echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    >&2 echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    >&2 echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    >&2 echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    >&2 echo
    >&2 echo -e "${CYAN}=== $1 ===${NC}"
    >&2 echo
}

# Remote query helpers (use plugin REST endpoints instead of hardcoded Solr calls)
REMOTE_CONFIG_ID="solr_demo"
# Index where remote search configurations are stored (must exist before POST)
REMOTE_SEARCH_CONFIG_INDEX="search-relevance-remote-search-config"

create_remote_config_solr() {
    log_info "Creating/validating remote search configuration for Solr..."

    local payload
    payload=$(cat <<'JSON'
{
  "id": "solr_demo",
  "name": "Solr Remote Search",
  "description": "Local Solr core via Docker",
  "connectionUrl": "http://localhost:8983/solr/ecommerce/select",
  "queryTemplate": "defType=edismax&q=${queryText}&q.op=OR&mm=2&lt=1&qf=title^0.2+category^0.5+bullet_points^0.2+description^3.0+brand^5.0+color^2.0&pf=brand^8+description^5&ps=1&tie=0.0&fq=brand:AVACRAFT&wt=json&rows=${size}",
  "maxRequestsPerSecond": 10,
  "maxConcurrentRequests": 5,
  "cacheDurationMinutes": 60
}
JSON
)

    # Create (idempotent) remote config
    local resp http_code body
    resp=$(curl -s -w "%{http_code}" -X POST "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations" \
        -H "Content-Type: application/json" \
        -d "$payload")
    http_code="${resp: -3}"
    body="${resp%???}"

    if [[ "$http_code" == "200" || "$http_code" == "201" || "$http_code" == "409" ]]; then
        log_info "Remote config create returned HTTP $http_code"
    else
        log_error "Failed to create remote search configuration (HTTP $http_code)"
        if command -v jq &> /dev/null; then
            echo "$body" | jq '.'
        else
            echo "$body"
        fi
        return 1
    fi

    # Verify remote config exists
    resp=$(curl -s -w "%{http_code}" "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations/$REMOTE_CONFIG_ID")
    http_code="${resp: -3}"
    body="${resp%???}"
    if [[ "$http_code" != "200" ]]; then
        log_error "Failed to fetch remote search configuration '$REMOTE_CONFIG_ID' (HTTP $http_code)"
        if command -v jq &> /dev/null; then
            echo "$body" | jq '.'
        else
            echo "$body"
        fi
        return 1
    fi

    log_success "Remote search configuration '$REMOTE_CONFIG_ID' is available"
    return 0
}

remote_search_execute() {
    # Usage: remote_search_execute "<query_text>" [size]
    local query_text="$1"
    local size="${2:-3}"

    local payload
    payload=$(cat <<JSON
{
  "remoteConfigId": "$REMOTE_CONFIG_ID",
  "queryText": "$query_text",
  "size": $size
}
JSON
)

    local resp http_code body
    resp=$(curl -s -w "%{http_code}" -X POST "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search/execute" \
        -H "Content-Type: application/json" \
        -d "$payload")
    http_code="${resp: -3}"
    body="${resp%???}"

    if [[ "$http_code" != "200" ]]; then
        log_error "Remote execute failed (HTTP $http_code). Ensure remote querying REST endpoints are enabled."
        if command -v jq &> /dev/null; then
            echo "$body" | jq '.'
        else
            echo "$body"
        fi
        return 1
    fi

    # Echo body to stdout for caller to consume
    echo "$body"
    return 0
}

remote_endpoints_available() {
    # Returns 0 if remote endpoints are available, 1 otherwise.
    # We check the GET route and also attempt to enable the workbench setting if we get 403.
    local resp http_code body
    resp=$(curl -s -w "%{http_code}" "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations" || true)
    http_code="${resp: -3}"
    body="${resp%???}"

    if [[ "$http_code" == "200" ]]; then
        return 0
    fi

    if [[ "$http_code" == "403" ]]; then
        # Try to enable the Workbench setting and re-check once
        if command -v curl >/dev/null 2>&1; then
            local payload='{"persistent":{"plugins.search_relevance.workbench_enabled": true}}'
            curl -s -X PUT "$OPENSEARCH_URL/_cluster/settings" -H "Content-Type: application/json" -d "$payload" >/dev/null 2>&1 || true
        fi
        resp=$(curl -s -w "%{http_code}" "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations" || true)
        http_code="${resp: -3}"
        if [[ "$http_code" == "200" ]]; then
            return 0
        fi
    fi

    # If we get 400 with "no handler found", the running plugin version likely doesn't include remote endpoints
    return 1
}


# Enhanced error handling
handle_error() {
    local exit_code=$?
    log_error "Script failed with exit code $exit_code"
    cleanup
    exit $exit_code
}

trap handle_error ERR

# Check dependencies
check_dependencies() {
    log_info "Checking dependencies..."

    local missing_deps=()
    for cmd in docker curl jq; do
        if ! command -v $cmd &> /dev/null; then
            missing_deps+=("$cmd")
        fi
    done

    if [ ${#missing_deps[@]} -ne 0 ]; then
        log_error "Missing required dependencies: ${missing_deps[*]}"
        log_info "Please install the missing dependencies (jq is required to compute metrics) and try again"
        exit 1
    fi

    log_success "All required dependencies are available"
}

# Wait for service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=${3:-30}
    local attempt=1

    log_info "Waiting for $service_name to be ready at $url..."

    while [ $attempt -le $max_attempts ]; do
        if curl -s --max-time 5 "$url" > /dev/null 2>&1; then
            log_success "$service_name is ready"
            return 0
        fi

        if [ $((attempt % 5)) -eq 0 ]; then
            log_info "Still waiting for $service_name... (attempt $attempt/$max_attempts)"
        else
            echo -n "."
        fi
        sleep 2
        ((attempt++))
    done

    echo
    log_error "$service_name failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# Wait for Solr core to be fully ready
wait_for_solr_core() {
    local core_name=$1
    local max_attempts=${2:-30}
    local attempt=1

    log_info "Waiting for Solr core '$core_name' to be fully ready..."

    while [ $attempt -le $max_attempts ]; do
        local core_status=$(curl -s "$SOLR_URL/solr/admin/cores?action=STATUS&core=$core_name" 2>/dev/null)

        # Check if core exists and is active
        if echo "$core_status" | grep -q "\"$core_name\"" && echo "$core_status" | grep -q '"instanceDir"'; then
            # Additional check: try to ping the core
            if curl -s "$SOLR_URL/solr/$core_name/admin/ping" > /dev/null 2>&1; then
                log_success "Solr core '$core_name' is fully ready"
                return 0
            fi
        fi

        if [ $((attempt % 5)) -eq 0 ]; then
            log_info "Still waiting for core '$core_name'... (attempt $attempt/$max_attempts)"
        else
            echo -n "."
        fi
        sleep 3
        ((attempt++))
    done

    echo
    log_error "Solr core '$core_name' failed to become ready within $((max_attempts * 3)) seconds"
    return 1
}

# Start Solr container with comprehensive error handling
start_solr() {
    log_info "Starting Solr container..."

    # Check if Docker is running
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi

    # Check if Solr container is already running
    if docker ps --format "table {{.Names}}" | grep -q "^solr_demo$"; then
        log_warning "Solr container already running, stopping it first..."
        docker stop solr_demo || true
        docker rm solr_demo || true
        sleep 2
    fi

    # Remove any existing container with the same name
    if docker ps -a --format "table {{.Names}}" | grep -q "^solr_demo$"; then
        log_info "Removing existing Solr container..."
        docker rm solr_demo || true
    fi

    # Check if port 8983 is available (use lsof on macOS if netstat fails)
    if command -v netstat &> /dev/null && netstat -tuln 2>/dev/null | grep -q ":8983 "; then
        log_error "Port 8983 is already in use. Please stop the service using this port."
        exit 1
    elif command -v lsof &> /dev/null && lsof -i :8983 &> /dev/null; then
        log_error "Port 8983 is already in use. Please stop the service using this port."
        exit 1
    fi

    # Start Solr container
    log_info "Starting new Solr container..."
    if docker run -d \
        --name solr_demo \
        -p 8983:8983 \
        solr:9 \
        solr-precreate $SOLR_CORE; then
        SOLR_CONTAINER_STARTED=true
        log_success "Solr container started successfully"
    else
        log_error "Failed to start Solr container"
        exit 1
    fi

    # Wait for Solr to be ready
    if ! wait_for_service "$SOLR_URL/solr/admin/cores" "Solr" 60; then
        log_error "Solr failed to start properly"
        exit 1
    fi

    # Wait for the specific core to be fully ready
    if ! wait_for_solr_core "$SOLR_CORE" 60; then
        log_error "Solr core '$SOLR_CORE' failed to initialize properly"
        exit 1
    fi
}

# Configure Solr schema for ESCI data
configure_solr_schema() {
    log_info "Configuring Solr schema for ESCI data..."

    # Double-check that core is ready and responsive
    local core_status=$(curl -s "$SOLR_URL/solr/admin/cores?action=STATUS&core=$SOLR_CORE")
    if ! echo "$core_status" | grep -q "\"$SOLR_CORE\""; then
        log_error "Solr core '$SOLR_CORE' not found"
        exit 1
    fi

    # Additional wait to ensure core is fully initialized
    log_info "Ensuring core is fully initialized..."
    sleep 5

    # Add field definitions for ESCI data structure - one field at a time
    local fields=("title:text_general" "category:text_general" "bullet_points:text_general" "description:text_general" "brand:string" "color:string")

    for field_def in "${fields[@]}"; do
        local field_name="${field_def%:*}"
        local field_type="${field_def#*:}"

        log_info "Adding field: $field_name ($field_type)"

        local schema_update='{
            "add-field": {
                "name": "'$field_name'",
                "type": "'$field_type'",
                "stored": true,
                "indexed": true
            }
        }'

        local response=$(curl -s -w "%{http_code}" -X POST "$SOLR_URL/solr/$SOLR_CORE/schema" \
            -H "Content-Type: application/json" \
            -d "$schema_update")

        local http_code="${response: -3}"
        local response_body="${response%???}"

        if [[ "$http_code" =~ ^(200|400)$ ]]; then
            # 400 is acceptable as field might already exist
            log_info "Field $field_name added successfully (or already exists)"
        else
            log_warning "Failed to add field $field_name (HTTP $http_code): $response_body"
        fi

        sleep 1
    done

    log_success "Solr schema configuration completed"
}

# Download data file with error handling
download_data_file() {
    if [ ! -f "$ECOMMERCE_DATA_FILE" ]; then
        log_info "Downloading ESCI data file..."
        local data_url="https://o19s-public-datasets.s3.amazonaws.com/esci_us_opensearch-2025-06-06.json"

        if command -v wget &> /dev/null; then
            if ! wget -q --timeout=30 --tries=3 "$data_url"; then
                log_error "Failed to download data file with wget"
                exit 1
            fi
        elif command -v curl &> /dev/null; then
            if ! curl -s --max-time 30 --retry 3 -O "$data_url"; then
                log_error "Failed to download data file with curl"
                exit 1
            fi
        else
            log_error "Neither wget nor curl available for downloading data file"
            exit 1
        fi

        # Verify file was downloaded and is not empty
        if [ ! -s "$ECOMMERCE_DATA_FILE" ]; then
            log_error "Downloaded data file is empty or corrupted"
            exit 1
        fi

        log_success "Data file downloaded successfully"
    else
        log_info "Data file already exists, skipping download"
    fi
}

# Build a tiny NDJSON subset from judgments (keeps index+doc pairs)
build_subset_from_judgments() {
    local judgments_file="$1"
    local subset_file="esci_us_subset.ndjson"

    # Fast path: if subset already exists and is non-empty, skip rebuild
    if [ -s "$subset_file" ]; then
        log_info "Subset file already exists ($subset_file); skipping rebuild"
        echo "$subset_file"
        return 0
    fi

    log_info "Building subset NDJSON from judgments at $judgments_file"

    if [ ! -f "$judgments_file" ]; then
        log_error "Judgments file not found: $judgments_file"
        return 1
    fi

    # Extract unique docIds
    local docids_file
    docids_file=$(mktemp)
    TEMP_FILES+=("$docids_file")
    if ! jq -r '.judgmentRatings[].ratings[].docId' "$judgments_file" | sort -u > "$docids_file"; then
        log_error "Failed to extract docIds from judgments"
        return 1
    fi

    # Verify source data is present
    if [ ! -f "$ECOMMERCE_DATA_FILE" ]; then
        log_error "Source data file not found for subset extraction: $ECOMMERCE_DATA_FILE"
        return 1
    fi

    # Build subset using awk membership check (portable on macOS/BSD awk)
    awk -v idsfile="$docids_file" '
      BEGIN {
        # Load docIds into a map
        while ((getline id < idsfile) > 0) {
          ids[id] = 1
        }
        prev = ""
      }
      # Remember index action lines to pair with next doc
      /^[[:space:]]*{"index"/ {
        prev = $0
        next
      }
      {
        # Check if this line contains any of our target docIds
        found = 0
        for (id in ids) {
          if (index($0, id)) {
            found = 1
            break
          }
        }
        if (found) {
          if (prev != "") print prev
          print $0
        }
        prev = ""
      }
    ' "$ECOMMERCE_DATA_FILE" > "$subset_file"

    if [ ! -s "$subset_file" ]; then
        log_error "Subset file is empty; no matching documents found"
        return 1
    fi

    log_success "Subset built: $subset_file ($(wc -l < "$subset_file") lines)"
    echo "$subset_file"
}

# Transform OpenSearch NDJSON to Solr JSON format
transform_data_for_solr() {
    log_info "Transforming ESCI data for Solr..."

    download_data_file

    # Transform NDJSON to Solr JSON format
    local solr_data_file="esci_us_solr.json"
    TEMP_FILES+=("$solr_data_file")

    log_info "Converting data format..."

    # Create Solr-compatible JSON
    echo '{"add": [' > "$solr_data_file"

    # Process the NDJSON file and convert to Solr format
    local first_doc=true
    local doc_count=0

    while IFS= read -r line; do
        # Skip index lines (they start with {"index":)
        if [[ $line == *'"index"'* ]]; then
            continue
        fi

        # Validate JSON line
        # Skip empty lines
        if [ -z "$line" ]; then
            continue
        fi

        if ! echo "$line" | jq empty 2>/dev/null; then
            log_warning "Skipping invalid JSON line"
            continue
        fi

        # Add comma separator for all but first document
        if [ "$first_doc" = false ]; then
            echo "," >> "$solr_data_file"
        fi
        first_doc=false

        # Transform the document with error handling
        if command -v jq &> /dev/null; then
            if ! echo "$line" | jq '{
                "doc": {
                    "id": (.asin // .id // "unknown"),
                    "title": (.title // ""),
                    "category": (if .category | type == "array" then .category | join(" > ") else (.category // "") end),
                    "bullet_points": (.bullet_points // .bullets // ""),
                    "description": (.description // ""),
                    "brand": (.brand // ""),
                    "color": (.color // "")
                }
            }' >> "$solr_data_file" 2>/dev/null; then
                log_warning "Failed to transform document, skipping"
                continue
            fi
        else
            # Fallback transformation without jq (basic sed/awk approach)
            # This is a simplified transformation that extracts basic fields
            local id=$(echo "$line" | sed -n 's/.*"asin":"\([^"]*\)".*/\1/p')
            if [ -z "$id" ]; then
                id=$(echo "$line" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
            fi
            local title=$(echo "$line" | sed -n 's/.*"title":"\([^"]*\)".*/\1/p' | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
            local brand=$(echo "$line" | sed -n 's/.*"brand":"\([^"]*\)".*/\1/p' | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
            local color=$(echo "$line" | sed -n 's/.*"color":"\([^"]*\)".*/\1/p' | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')

            if [ -n "$id" ]; then
                cat >> "$solr_data_file" << EOF
{
  "doc": {
    "id": "$id",
    "title": "$title",
    "category": "",
    "bullet_points": "",
    "description": "",
    "brand": "$brand",
    "color": "$color"
  }
}
EOF
            else
                log_warning "Failed to extract document ID, skipping"
                continue
            fi
        fi

        ((doc_count++))

    done < "$ECOMMERCE_DATA_FILE"

    echo ']}' >> "$solr_data_file"

    if [ $doc_count -eq 0 ]; then
        log_error "No documents were successfully transformed"
        return 1
    fi

    log_success "Data transformation completed: $solr_data_file ($doc_count documents)"
    echo "$solr_data_file"
}

# Load data into Solr
load_data_to_solr() {
    local solr_data_file=$1

    log_info "Loading data into Solr..."

    # Verify file exists and is not empty
    if [ ! -s "$solr_data_file" ]; then
        log_error "Solr data file is missing or empty"
        exit 1
    fi

    # Ensure a clean core before loading to avoid duplicate counts
    curl -s -X POST "$SOLR_URL/solr/$SOLR_CORE/update?commit=true" \
        -H "Content-Type: application/json" \
        -d '{"delete":{"query":"*:*"}}' > /dev/null 2>&1 || true

    local response=$(curl -s -w "%{http_code}" -X POST "$SOLR_URL/solr/$SOLR_CORE/update?commit=true&overwrite=true" \
        -H "Content-Type: application/json" \
        -d @"$solr_data_file")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [ "$http_code" != "200" ]; then
        log_error "Failed to load data into Solr (HTTP $http_code)"
        echo "Response: $response_body"
        exit 1
    fi

    # Wait a moment for commit to complete
    sleep 2

    # Verify data was loaded
    local doc_count_response=$(curl -s "$SOLR_URL/solr/$SOLR_CORE/select?q=*:*&rows=0")
    if command -v jq &> /dev/null; then
        local doc_count=$(echo "$doc_count_response" | jq -r '.response.numFound // 0')
    else
        local doc_count=$(echo "$doc_count_response" | grep -o '"numFound":[0-9]*' | cut -d: -f2 || echo "0")
    fi

    if [ "$doc_count" -eq 0 ]; then
        log_error "No documents found in Solr after loading"
        exit 1
    fi

    log_success "Loaded $doc_count documents into Solr"
}

# Setup OpenSearch data
setup_opensearch_data() {
    log_info "Setting up OpenSearch data..."

    # Wait for OpenSearch to be ready
    if ! wait_for_service "$OPENSEARCH_URL" "OpenSearch" 30; then
        log_error "OpenSearch is not available at $OPENSEARCH_URL"
        log_info "Start OpenSearch and install the Search Relevance plugin, then re-run this script."
        exit 1
    fi

    # Check if search relevance plugin is available
    local plugins_response=$(curl -s "$OPENSEARCH_URL/_cat/plugins")
    if ! echo "$plugins_response" | grep -q "search-relevance"; then
        log_error "Search Relevance plugin is not installed or enabled"
        log_info "Please ensure the plugin is installed and the cluster setting is enabled:"
        log_info "PUT /_cluster/settings"
        log_info '{"persistent": {"plugins.search_relevance.workbench_enabled": true}}'
        exit 1
    fi

    # Ensure Workbench is enabled (required for Search Relevance REST APIs)
    local settings_payload='{"persistent":{"plugins.search_relevance.workbench_enabled": true}}'
    local set_resp set_code
    set_resp=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_cluster/settings" -H "Content-Type: application/json" -d "$settings_payload" || true)
    set_code="${set_resp: -3}"
    if [[ "$set_code" != "200" ]]; then
        log_warning "Failed to enable Workbench setting (HTTP $set_code); remote endpoints may be unavailable."
    else
        log_info "Workbench setting enabled"
    fi

    # Clean up existing data
    log_info "Cleaning up existing OpenSearch data..."
    curl -s -X DELETE "$OPENSEARCH_URL/ecommerce" > /dev/null 2>&1 || true
    curl -s -X DELETE "$OPENSEARCH_URL/search-relevance-*" > /dev/null 2>&1 || true
    curl -s -X DELETE "$OPENSEARCH_URL/.plugins-search-relevance-*" > /dev/null 2>&1 || true

    sleep 2

    download_data_file

    # Load ESCI data into OpenSearch
    log_info "Loading data into OpenSearch ecommerce index..."

    # Load data in smaller chunks for reliability
    local chunk_size=100
    local total_lines
    total_lines=$(wc -l < "$ECOMMERCE_DATA_FILE" 2>/dev/null || echo "1000")
    # Load the entire dataset for parity with Solr
    local max_lines=$total_lines
    local chunks=$(( (max_lines + chunk_size - 1) / chunk_size ))

    for (( i=0; i<chunks; i++ )); do
        local start_line=$(( i * chunk_size + 1 ))
        local end_line=$(( (i + 1) * chunk_size ))

        if [ $end_line -gt $max_lines ]; then
            end_line=$max_lines
        fi

        log_info "Loading chunk $((i+1))/$chunks (lines $start_line-$end_line)"

        local chunk_data=$(sed -n "${start_line},${end_line}p" "$ECOMMERCE_DATA_FILE" 2>/dev/null || echo "")
        if [ -z "$chunk_data" ]; then
            log_warning "No data in chunk $((i+1)), skipping"
            continue
        fi

        local response=$(echo "$chunk_data" | curl -s -w "%{http_code}" -X POST "$OPENSEARCH_URL/ecommerce/_bulk" \
            -H 'Content-Type: application/x-ndjson' \
            --data-binary @-)

        local http_code="${response: -3}"
        if [ "$http_code" != "200" ]; then
            log_warning "Failed to load chunk $((i+1)) (HTTP $http_code)"
        fi

        sleep 1
    done

    # Refresh index
    curl -s -X POST "$OPENSEARCH_URL/ecommerce/_refresh" > /dev/null

    # Verify data was loaded
    local doc_count_response=$(curl -s "$OPENSEARCH_URL/ecommerce/_count")
    if command -v jq &> /dev/null; then
        local doc_count=$(echo "$doc_count_response" | jq -r '.count // 0')
    else
        local doc_count=$(echo "$doc_count_response" | grep -o '"count":[0-9]*' | cut -d: -f2 || echo "0")
    fi

    if [ "$doc_count" -eq 0 ]; then
        log_error "No documents found in OpenSearch after loading"
        exit 1
    fi

    log_success "Loaded $doc_count documents into OpenSearch"
}

# Create local search configuration
create_local_search_config() {
    log_info "Creating local search configuration..."

    local config_payload=$(cat <<'JSON'
{
    "name": "opensearch_local",
    "index": "ecommerce",
    "query": "{\"query\": {\"multi_match\": {\"query\": \"%SearchText%\", \"fields\": [\"title^2\", \"category\", \"bullet_points\", \"description\", \"brand\", \"color\"]}}}",
    "searchPipeline": ""
}
JSON
)

    local response=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations" \
        -H "Content-Type: application/json" \
        -d "$config_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201)$ ]]; then
        local config_id=$(echo "$response_body" | jq -r '.search_configuration_id // .id')
        CREATED_RESOURCES+=("search_config:$config_id")
        log_success "Local search configuration created with ID: $config_id"
        echo "$config_id"
    else
        log_error "Failed to create local search configuration (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create remote search configuration for experiments
create_remote_search_config_for_experiment() {
    log_info "Creating remote search configuration for experiment..."

    local config_payload=$(cat <<'JSON'
{
    "id": "solr_remote",
    "name": "Solr Remote Search",
    "description": "Remote Solr search configuration for experiment comparison",
    "connectionUrl": "http://localhost:8983/solr/ecommerce/select",
    "queryTemplate": "defType=edismax&q=${queryText}&q.op=OR&mm=2&lt=1&qf=title^0.2+category^0.5+bullet_points^0.2+description^3.0+brand^5.0+color^2.0&pf=brand^8+description^5&ps=1&tie=0.0&fq=brand:AVACRAFT&wt=json&rows=${size}",
    "maxRequestsPerSecond": 10,
    "maxConcurrentRequests": 5,
    "cacheDurationMinutes": 60
}
JSON
)

    local response=$(curl -s -w "%{http_code}" -X POST "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations" \
        -H "Content-Type: application/json" \
        -d "$config_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201|409)$ ]]; then
        CREATED_RESOURCES+=("remote_config:solr_remote")
        log_success "Remote search configuration created/updated: solr_remote"
        echo "solr_remote"
    else
        log_error "Failed to create remote search configuration (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create a second remote search configuration (variant) with an exclusion to force ranking/coverage differences
create_remote_search_config_variant() {
    log_info "Creating remote search configuration VARIANT for experiment..."

    local config_payload=$(cat <<'JSON'
{
    "id": "solr_remote_variant",
    "name": "Solr Remote Search (Variant)",
    "description": "Variant remote Solr search configuration to produce different rankings/coverage",
    "connectionUrl": "http://localhost:8983/solr/ecommerce/select",
    "queryTemplate": "defType=edismax&q=${queryText}&q.op=OR&mm=2&lt=1&qf=title^0.2+category^0.5+bullet_points^0.2+description^3.0+brand^5.0+color^2.0&pf=brand^8+description^5&ps=1&tie=0.0&fq=-brand:AVACRAFT&wt=json&rows=${size}",
    "maxRequestsPerSecond": 10,
    "maxConcurrentRequests": 5,
    "cacheDurationMinutes": 60
}
JSON
)

    local response=$(curl -s -w "%{http_code}" -X POST "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations" \
        -H "Content-Type: application/json" \
        -d "$config_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201|409)$ ]]; then
        CREATED_RESOURCES+=("remote_config:solr_remote_variant")
        log_success "Remote search configuration VARIANT created/updated: solr_remote_variant"
        echo "solr_remote_variant"
    else
        log_error "Failed to create remote search configuration VARIANT (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create query set from ImportJudgments.json (aligns with judged queries)
create_query_set_from_judgments() {
    log_info "Creating query set from ImportJudgments.json"

    local judgments_file="$SCRIPT_DIR/../resources/judgment/ImportJudgments.json"
    if [ ! -f "$judgments_file" ]; then
        log_error "Judgments file not found: $judgments_file"
        exit 1
    fi

    # Build unique queries array, map to [{queryText: "..."}]
    local queries_json
    queries_json=$(jq -c '[.judgmentRatings[].query] | unique | map({queryText: .})' "$judgments_file")
    if [ -z "$queries_json" ]; then
        log_error "Failed to build queries from judgments"
        exit 1
    fi

    local queryset_payload
    queryset_payload=$(jq -n \
        --arg name "$QUERY_SET_NAME" \
        --arg description "Queries derived from ImportJudgments.json" \
        --arg sampling "manual" \
        --argjson qs "$queries_json" \
        '{
            name: $name,
            description: $description,
            sampling: $sampling,
            querySetQueries: $qs
        }')

    local response
    response=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" \
        -H "Content-Type: application/json" \
        -d "$queryset_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201)$ ]]; then
        local queryset_id
        queryset_id=$(echo "$response_body" | jq -r '.query_set_id // .id')
        CREATED_RESOURCES+=("queryset:$queryset_id")
        log_success "Query set created with ID: $queryset_id"
        echo "$queryset_id"
    else
        log_error "Failed to create query set (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create query set from ESCI data
create_query_set() {
    log_info "Creating query set from ESCI data..."

    local queryset_file="$SCRIPT_DIR/../data-esci/esci_us_queryset.json"
    if [ ! -f "$queryset_file" ]; then
        log_error "Query set file not found: $queryset_file"
        exit 1
    fi

    # Extract first 10 query objects and create payload with correct schema
    log_info "Debug: Extracting query objects from $queryset_file"
    local queries_obj
    queries_obj=$(jq '.querySetQueries[:10]' "$queryset_file")
    log_info "Debug: Extracted querySetQueries JSON: ${queries_obj:0:100}..."
    
    local queryset_payload
    queryset_payload=$(jq -n \
        --arg name "$QUERY_SET_NAME" \
        --arg description "ESCI demo queries for local vs remote comparison" \
        --arg sampling "manual" \
        --argjson qs "$queries_obj" \
        '{
            name: $name,
            description: $description,
            sampling: $sampling,
            querySetQueries: $qs
        }')
    
    log_info "Debug: Created payload: ${queryset_payload:0:200}..."

    local response=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets" \
        -H "Content-Type: application/json" \
        -d "$queryset_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201)$ ]]; then
        local queryset_id=$(echo "$response_body" | jq -r '.query_set_id // .id')
        CREATED_RESOURCES+=("queryset:$queryset_id")
        log_success "Query set created with ID: $queryset_id"
        echo "$queryset_id"
    else
        log_error "Failed to create query set (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create judgment set from ESCI data
create_judgment_set() {
    log_info "Creating judgment set from ESCI data..."

    local judgments_file="$SCRIPT_DIR/../resources/judgment/ImportJudgments.json"
    if [ ! -f "$judgments_file" ]; then
        log_error "Judgments file not found: $judgments_file"
        exit 1
    fi

    # Use existing judgments structure
    local judgments_payload=$(cat "$judgments_file")

    local response=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/judgments" \
        -H "Content-Type: application/json" \
        -d "$judgments_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201)$ ]]; then
        local judgment_id=$(echo "$response_body" | jq -r '.judgment_id // .id')
        CREATED_RESOURCES+=("judgment:$judgment_id")
        log_success "Judgment set created with ID: $judgment_id"
        echo "$judgment_id"
    else
        log_error "Failed to create judgment set (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create pairwise comparison experiment
create_experiment() {
    local local_config_id=$1
    local remote_config_id=$2
    local queryset_id=$3
    local judgment_id=$4

    log_info "Creating pairwise comparison experiment..."

    local experiment_payload=$(cat <<JSON
{
    "querySetId": "$queryset_id",
    "searchConfigurationList": ["$local_config_id", "$remote_config_id"],
    "judgmentList": ["$judgment_id"],
    "size": 10,
    "type": "PAIRWISE_COMPARISON"
}
JSON
)

    local response=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
        -H "Content-Type: application/json" \
        -d "$experiment_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201)$ ]]; then
        local experiment_id=$(echo "$response_body" | jq -r '.experiment_id // .id')
        CREATED_RESOURCES+=("experiment:$experiment_id")
        log_success "Experiment created with ID: $experiment_id"
        echo "$experiment_id"
    else
        log_error "Failed to create experiment (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create remote-only experiment (REMOTE_SEARCH_EVALUATION) using remoteConfigId list
create_remote_experiment() {
    local remote_config_id=$1
    local queryset_id=$2
    local judgment_id=$3

    log_info "Creating REMOTE_SEARCH_EVALUATION experiment (remoteConfigId: $remote_config_id)..."

    local experiment_payload=$(cat <<JSON
{
    "querySetId": "$queryset_id",
    "searchConfigurationList": ["$remote_config_id"],
    "judgmentList": ["$judgment_id"],
    "size": 10,
    "type": "REMOTE_SEARCH_EVALUATION"
}
JSON
)

    local response=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
        -H "Content-Type: application/json" \
        -d "$experiment_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201)$ ]]; then
        local experiment_id=$(echo "$response_body" | jq -r '.experiment_id // .id')
        CREATED_RESOURCES+=("experiment:$experiment_id")
        log_success "Remote experiment created with ID: $experiment_id"
        echo "$experiment_id"
    else
        log_error "Failed to create remote experiment (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Create local-only experiment (POINTWISE_EVALUATION) using a single local search configuration
create_local_experiment() {
    local local_config_id=$1
    local queryset_id=$2
    local judgment_id=$3

    log_info "Creating POINTWISE_EVALUATION experiment (local config: $local_config_id)..."

    local experiment_payload=$(cat <<JSON
{
    "querySetId": "$queryset_id",
    "searchConfigurationList": ["$local_config_id"],
    "judgmentList": ["$judgment_id"],
    "size": 10,
    "type": "POINTWISE_EVALUATION"
}
JSON
)

    local response=$(curl -s -w "%{http_code}" -X PUT "$OPENSEARCH_URL/_plugins/_search_relevance/experiments" \
        -H "Content-Type: application/json" \
        -d "$experiment_payload")

    local http_code="${response: -3}"
    local response_body="${response%???}"

    if [[ "$http_code" =~ ^(200|201)$ ]]; then
        local experiment_id=$(echo "$response_body" | jq -r '.experiment_id // .id')
        CREATED_RESOURCES+=("experiment:$experiment_id")
        log_success "Local experiment created with ID: $experiment_id"
        echo "$experiment_id"
    else
        log_error "Failed to create local experiment (HTTP $http_code)"
        echo "$response_body" | jq '.' || echo "$response_body"
        exit 1
    fi
}

# Wait for experiment completion
wait_for_experiment() {
    local experiment_id=$1
    local max_attempts=60
    local attempt=1

    log_info "Waiting for experiment to complete..."

    while [ $attempt -le $max_attempts ]; do
        local response=$(curl -s "$OPENSEARCH_URL/_plugins/_search_relevance/experiments/$experiment_id")
        # Extract status from the first hit's _source since the API returns an OpenSearch SearchResponse
        local status=$(echo "$response" | jq -r '.hits.hits[0]._source.status // "unknown"')

        case "$status" in
            "COMPLETED")
                log_success "Experiment completed successfully"
                return 0
                ;;
            "ERROR")
                log_error "Experiment failed"
                echo "$response" | jq '.'
                return 1
                ;;
            "PROCESSING")
                if [ $((attempt % 10)) -eq 0 ]; then
                    log_info "Experiment still running... (attempt $attempt/$max_attempts)"
                else
                    echo -n "."
                fi
                ;;
            *)
                log_warning "Unknown experiment status: $status"
                ;;
        esac

        sleep 5
        ((attempt++))
    done

    echo
    log_error "Experiment did not complete within $((max_attempts * 5)) seconds"
    return 1
}

# Show experiment results
show_experiment_results() {
    local experiment_id=$1

    log_info "Retrieving experiment results..."

    local response=$(curl -s "$OPENSEARCH_URL/_plugins/_search_relevance/experiments/$experiment_id")
    
    if command -v jq &> /dev/null; then
        echo "$response" | jq '.'
        
        echo
        log_info "Experiment Summary:"
        echo "$response" | jq -r '
            .hits.hits[0]._source as $s |
            "Experiment ID: " + ($s.id // "unknown") + "\n" +
            "Status: " + ($s.status // "unknown") + "\n" +
            "Type: " + ($s.type // "unknown") + "\n" +
            "Query Set: " + ($s.querySetId // "unknown") + "\n" +
            "Search Configurations: " + (($s.searchConfigurationList // []) | join(", "))
        '
        
        # Show metrics if available
        local metrics=$(echo "$response" | jq '.metrics // empty')
        if [ -n "$metrics" ] && [ "$metrics" != "null" ]; then
            echo
            log_info "IR Metrics Comparison:"
            echo "$metrics" | jq '.'
        fi
    else
        echo "$response"
    fi
}

# Fetch and print persisted IR metrics from evaluation_result index
fetch_and_print_metrics() {
    local experiment_id=$1

    log_info "Fetching persisted IR metrics for experiment $experiment_id from evaluation_result index..."

    # Build request payload for evaluation_result documents of this experiment
    local query_payload='{"size":1000,"_source":{"includes":["experimentId","metrics.metric","metrics.value"]},"query":{"term":{"experimentId":"'"$experiment_id"'"}}}'

    local resp
    resp=$(curl -s -X POST "$OPENSEARCH_URL/search-relevance-evaluation-result/_search" \
        -H "Content-Type: application/json" \
        -d "$query_payload")

    if ! command -v jq >/dev/null 2>&1; then
        log_warning "jq not available - showing raw evaluation_result response"
        echo "$resp"
        return 0
    fi

    # Validate JSON
    if ! echo "$resp" | jq empty >/dev/null 2>&1; then
        log_warning "Invalid JSON from evaluation_result search"
        echo "$resp"
        return 0
    fi

    # Total hits (compat for 7.x/8.x)
    local total
    total=$(echo "$resp" | jq -r '(.hits.total.value // .hits.total // 0)')

    if [[ -z "$total" || "$total" == "0" ]]; then
        log_warning "No evaluation results found for experimentId=$experiment_id"
        return 0
    fi

    log_info "Aggregated metrics (across all evaluation results):"
    echo "$resp" | jq -r '
      [.hits.hits[]?._source] as $docs
      | ($docs | length) as $n
      | if $n == 0 then empty else
          ($docs
           | map(.metrics // [])
           | add
           | group_by(.metric)
           | map({metric: .[0].metric, avg: (map(.value // 0) | add / length)})
           | sort_by(.metric)
           | map("• " + .metric + ": " + (.avg | tostring))
          )[]
        end
    ' 2>/dev/null || {
        log_warning "Failed to compute aggregated metrics from evaluation_result"
        echo "$resp" | jq '.' 2>/dev/null || echo "$resp"
    }

    return 0
}

# Compute aggregated metrics as JSON for an experiment (metric -> avg)
compute_aggregated_metrics_json() {
    local experiment_id=$1

    # Build request payload for evaluation_result documents of this experiment
    local query_payload='{"size":1000,"_source":{"includes":["experimentId","metrics.metric","metrics.value"]},"query":{"term":{"experimentId":"'"$experiment_id"'"}}}'

    local resp
    resp=$(curl -s -X POST "$OPENSEARCH_URL/search-relevance-evaluation-result/_search" \
        -H "Content-Type: application/json" \
        -d "$query_payload")

    # Require jq and valid JSON
    if ! command -v jq >/dev/null 2>&1 || ! echo "$resp" | jq empty >/dev/null 2>&1; then
        echo '{}'
        return 0
    fi

    # Total hits (compat for 7.x/8.x)
    local total
    total=$(echo "$resp" | jq -r '(.hits.total.value // .hits.total // 0)')
    if [[ -z "$total" || "$total" == "0" ]]; then
        echo '{}'
        return 0
    fi

    echo "$resp" | jq -r '
      [.hits.hits[]?._source.metrics[]?]
      | group_by(.metric)
      | map({key: .[0].metric, value: (map(.value // 0) | add / length)})
      | from_entries
    '
}

# Show side-by-side aggregated metrics for two experiments (remote vs local)
show_side_by_side_metrics() {
    local remote_experiment_id=$1
    local local_experiment_id=$2

    if ! command -v jq >/dev/null 2>&1; then
        log_warning "jq not available - cannot compute side-by-side metrics"
        return 0
    fi

    local remote_metrics local_metrics
    remote_metrics=$(compute_aggregated_metrics_json "$remote_experiment_id")
    local_metrics=$(compute_aggregated_metrics_json "$local_experiment_id")

    # If both are empty, nothing to compare
    if [[ "$remote_metrics" == "{}" && "$local_metrics" == "{}" ]]; then
        log_info "No persisted metrics found for either experiment to compare."
        return 0
    fi

    # Print comparison lines
    jq -n --argjson r "$remote_metrics" --argjson l "$local_metrics" '
      ([$r,$l] | add | keys | unique) as $keys
      | $keys
      | map("• " + . + ": remote=" + (($r[.] // "N/A") | tostring) + ", local=" + (($l[.] // "N/A") | tostring))
      | .[]
    '
}
show_metrics_comparison() {
    local experiment_id=$1

    log_info "Metrics Comparison (live)"
    log_info "========================"

    local response=$(curl -s "$OPENSEARCH_URL/_plugins/_search_relevance/experiments/$experiment_id")
    
    if command -v jq &> /dev/null; then
        # Check if experiment has metrics (from first hit's _source)
        local has_metrics=$(echo "$response" | jq -r '.hits.hits[0]._source | has("metrics")')
        local status=$(echo "$response" | jq -r '.hits.hits[0]._source.status // "unknown"')
        
        if [ "$has_metrics" = "true" ]; then
            echo "$response" | jq -r '
                (.hits.hits[0]._source) as $s |
                if $s.metrics then
                    "Configuration Comparison:\n" +
                    (($s.searchConfigurationList // []) | map("• " + .) | join("\n")) + "\n\n" +
                    "IR Metrics Results:\n" +
                    ($s.metrics | to_entries | map("• " + .key + ": " + (.value | tostring)) | join("\n"))
                else
                    "No metrics available in experiment results"
                end
            '
        elif [ "$status" = "COMPLETED" ]; then
            log_info "Experiment completed but no metrics found in response"
            log_info "This may indicate the experiment type doesn't generate comparative metrics"
        elif [ "$status" = "PROCESSING" ]; then
            log_info "Experiment is still running - metrics will be available when complete"
        elif [ "$status" = "ERROR" ]; then
            log_warning "Experiment failed - no metrics available"
            echo "$response" | jq -r '.error // "No error details available"'
        else
            log_warning "Experiment status: $status - metrics may not be available"
        fi
    else
        log_warning "jq not available - showing raw experiment response"
        echo "$response"
    fi
    
    echo
    log_info "Note: Metrics are computed by the Search Relevance Workbench experiment framework"
    log_info "using the ESCI judgment data for NDCG, MAP, MRR, and Precision@K calculations."
}

# Test query template transformation
test_query_template() {
    log_info "Testing query template via remote configuration..."

    local query_text="steel"
    local opensearch_query='{"query":{"multi_match":{"query":"'$query_text'","fields":["title","category","bullet_points","description","brand","color"]}}}'

    echo
    log_info "OpenSearch Query:"
    if command -v jq &> /dev/null; then
        echo "$opensearch_query" | jq '.'
    else
        echo "$opensearch_query"
    fi

    # Ensure remote config exists on the plugin
    if ! create_remote_config_solr; then
        log_error "Remote configuration setup failed; cannot validate template."
        return 1
    fi

    echo
    log_info "Remote configuration details:"
    local cfg
    cfg=$(curl -s "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations/$REMOTE_CONFIG_ID" || true)
    if command -v jq &> /dev/null; then
        echo "$cfg" | jq '{id,name,connectionUrl,queryTemplatePresent: (has("queryTemplate") and (.queryTemplate|length>0)), responseTemplatePresent: (has("responseTemplate") and (.responseTemplate|length>0))}'
        echo
        log_info "Query template preview (with queryText and size substituted):"
        local qtpl
        qtpl=$(echo "$cfg" | jq -r '.queryTemplate // ""')
        if [ -n "$qtpl" ]; then
            echo "$qtpl" | sed "s/\${queryText}/$query_text/g" | sed "s/\${size}/10/g"
        else
            log_warning "No queryTemplate present in remote configuration."
        fi
    else
        echo "$cfg"
    fi

    echo
    log_success "Remote query template configuration validated"
}

# Test response template transformation
test_response_template() {
    log_info "Testing response template mapping via remote query execution..."

    local query_text="steel"
    local size=3

    log_info "Executing remote search for query='$query_text', size=$size"
    local remote_resp
    if ! remote_resp=$(remote_search_execute "$query_text" "$size"); then
        log_error "Remote response mapping test failed due to execution error."
        return 1
    fi

    echo
    log_info "Mapped OpenSearch-like Response (from remote):"
    if command -v jq &> /dev/null; then
        echo "$remote_resp" | jq '.'
    else
        echo "$remote_resp"
    fi

    # Check if the response contains an error
    if command -v jq &> /dev/null; then
        if echo "$remote_resp" | jq -e '.error' > /dev/null 2>&1; then
            echo
            log_error "Remote response mapping failed - response contains error"
            local error_type=$(echo "$remote_resp" | jq -r '.error.type // "unknown"')
            local error_reason=$(echo "$remote_resp" | jq -r '.error.reason // "unknown reason"')
            log_error "Error type: $error_type"
            log_error "Error reason: $error_reason"
            return 1
        fi
    else
        # Fallback check without jq
        if echo "$remote_resp" | grep -q '"error"'; then
            echo
            log_error "Remote response mapping failed - response contains error"
            return 1
        fi
    fi

    echo
    log_success "Remote response mapping validated through plugin endpoint"
}

# Demonstrate search comparison
demonstrate_search_comparison() {
    log_info "Demonstrating search comparison between OpenSearch (local) and Solr (remote via plugin)..."

    local test_queries=("metal frame" "steel" "keyboard" "iphone")

    for query in "${test_queries[@]}"; do
        log_info "Testing query: '$query'"

        # OpenSearch local baseline
        log_info "OpenSearch results:"
        local os_query='{
            "query": {
                "multi_match": {
                    "query": "'$query'",
                    "fields": ["title^2", "category", "bullet_points", "description", "brand", "color"]
                }
            },
            "size": 3
        }'

        local os_response
        os_response=$(curl -s -X POST "$OPENSEARCH_URL/ecommerce/_search" \
            -H "Content-Type: application/json" \
            -d "$os_query")

        # Enhanced null/empty response handling for OpenSearch
        if [ -z "$os_response" ] || [ "$os_response" = "null" ]; then
            echo "  (No response from OpenSearch)"
        elif command -v jq &> /dev/null; then
            # Validate JSON before processing with jq
            if echo "$os_response" | jq empty 2>/dev/null; then
                # Check if response has hits structure
                if echo "$os_response" | jq -e '.hits.hits' >/dev/null 2>&1; then
                    echo "$os_response" | jq -r '.hits.hits[] | "  - " + (._source.title // "No title") + " (Score: " + (._score | tostring) + ")"' 2>/dev/null | head -3 || echo "  (Error processing OpenSearch results)"
                else
                    echo "  (OpenSearch response missing hits structure)"
                fi
            else
                echo "  (Invalid JSON response from OpenSearch)"
                echo "  Debug: Response first 100 chars: ${os_response:0:100}"
            fi
        else
            echo "  (JSON formatting not available without jq)"
        fi

        # Remote (Solr via plugin)
        log_info "Remote (Solr via plugin) results:"
        local remote_resp
        if remote_resp=$(remote_search_execute "$query" 3); then
            # Enhanced null/empty response handling for remote
            if [ -z "$remote_resp" ] || [ "$remote_resp" = "null" ]; then
                echo "  (No response from remote search)"
            elif command -v jq &> /dev/null; then
                # Validate JSON before processing with jq
                if echo "$remote_resp" | jq empty 2>/dev/null; then
                    # Check if the response contains an error
                    if echo "$remote_resp" | jq -e '.error' > /dev/null 2>&1; then
                        log_warning "Remote execution returned error for query '$query'"
                        local error_type=$(echo "$remote_resp" | jq -r '.error.type // "unknown"')
                        echo "  Error: $error_type"
                    elif echo "$remote_resp" | jq -e '.hits.hits' >/dev/null 2>&1; then
                        # Handle both mapped OpenSearch format and potential Solr array formats
                        echo "$remote_resp" | jq -r '
                            .hits.hits[] |
                            "  - " + (
                                (._source.title | if type=="array" then .[0] else . end) //
                                (.title | if type=="array" then .[0] else . end) //
                                (._source.title) // (.title) // "No title"
                            ) + " (Score: " + (._score | tostring) + ")"
                        ' 2>/dev/null | head -3 || echo "  (Error processing remote results)"
                    else
                        echo "  (Remote response missing hits structure)"
                    fi
                else
                    echo "  (Invalid JSON response from remote search)"
                    echo "  Debug: Response first 100 chars: ${remote_resp:0:100}"
                fi
            else
                # Fallback check without jq
                if echo "$remote_resp" | grep -q '"error"'; then
                    log_warning "Remote execution returned error for query '$query'"
                    echo "  Error detected in response"
                else
                    echo "  (JSON formatting not available without jq)"
                fi
            fi
        else
            log_warning "Remote execution failed for query '$query'"
        fi

        echo
    done
}

# Demonstrate Solr-only search (when OpenSearch is not available)
demonstrate_solr_only_search() {
    log_info "Demonstrating Solr search directly (OpenSearch remote endpoints unavailable)"
    log_warning "Showing Solr results by querying Solr API without plugin integration"

    local test_queries=("metal frame" "steel" "keyboard" "iphone")

    for query in "${test_queries[@]}"; do
        log_info "Testing query: '$query'"

        # Direct Solr query
        local solr_params
        solr_params="q=title:(${query}) OR category:(${query}) OR bullet_points:(${query}) OR description:(${query}) OR brand:(${query}) OR color:(${query})&wt=json&rows=3"
        log_info "Solr results:"
        local solr_resp
        solr_resp=$(curl -s "$SOLR_URL/solr/$SOLR_CORE/select?${solr_params}" || true)
        if command -v jq &> /dev/null; then
            echo "$solr_resp" | jq -r '.response.docs[]? | "  - " + (.title // "No title")' | head -3
        else
            echo "  (JSON formatting not available without jq)"
        fi

        echo
    done

    log_info "Solr-only demonstration notes:"
    log_info "• Solr is queried directly via its select handler"
    log_info "• To use plugin-based remote execution, run with a Search Relevance plugin build that includes remote endpoints"
}

# Show remote search configuration concept
show_remote_search_concept() {
    log_info "Remote Search Configuration (Live)"
    log_info "=================================="

    local cfg
    cfg=$(curl -s "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations/$REMOTE_CONFIG_ID" || true)

    if [ -z "$cfg" ]; then
        log_warning "Could not fetch remote configuration '$REMOTE_CONFIG_ID'."
        return 0
    fi

    if command -v jq &> /dev/null; then
        echo "$cfg" | jq '{
            id, name, connectionUrl,
            maxRequestsPerSecond, maxConcurrentRequests, cacheDurationMinutes,
            queryTemplatePresent: (has("queryTemplate") and (.queryTemplate|length>0)),
            responseTemplatePresent: (has("responseTemplate") and (.responseTemplate|length>0))
        }'
    else
        echo "$cfg"
    fi

    echo
    log_info "How this demo used the configuration:"
    log_info "• Executed remote queries using remote_search/execute"
    log_info "• Query template transformed OpenSearch intent to Solr parameters"
    log_info "• Response template normalized Solr results to OpenSearch-like format"
}


# Data parity verification (counts)
verify_data_parity() {
    log_section "Verifying Data Parity Between OpenSearch and Solr"

    # OpenSearch count
    local os_count_resp os_count
    os_count_resp=$(curl -s "$OPENSEARCH_URL/ecommerce/_count" || true)
    if command -v jq &> /dev/null && echo "$os_count_resp" | jq empty >/dev/null 2>&1; then
        os_count=$(echo "$os_count_resp" | jq -r '.count // 0')
    else
        os_count=$(echo "$os_count_resp" | grep -o '"count":[0-9]*' | cut -d: -f2 || echo "0")
    fi

    # Solr count
    local solr_count_resp solr_count
    solr_count_resp=$(curl -s "$SOLR_URL/solr/$SOLR_CORE/select?q=*:*&rows=0" || true)
    if command -v jq &> /dev/null && echo "$solr_count_resp" | jq empty >/dev/null 2>&1; then
        solr_count=$(echo "$solr_count_resp" | jq -r '.response.numFound // 0')
    else
        solr_count=$(echo "$solr_count_resp" | grep -o '"numFound":[0-9]*' | cut -d: -f2 || echo "0")
    fi

    log_info "OpenSearch document count: $os_count"
    log_info "Solr document count:       $solr_count"

    if [ -n "$os_count" ] && [ -n "$solr_count" ] && [ "$os_count" -eq "$solr_count" ]; then
        log_success "Data parity check passed: counts match"
    else
        log_warning "Data parity check failed: counts differ"
    fi
}

# Cleanup function
cleanup() {
    if [ "$SKIP_CLEANUP" = true ]; then
        log_info "Cleanup skipped - Solr container and data preserved for iteration"
        log_info "To manually clean up later:"
        log_info "  docker stop solr_demo && docker rm solr_demo"
        
        # Show created resources for inspection
        if [ ${#CREATED_RESOURCES[@]} -gt 0 ]; then
            log_info "Created resources for inspection:"
            for resource in "${CREATED_RESOURCES[@]}"; do
                echo "  - $resource"
            done
        fi
        
        # Still clean up temporary files
        for file in "${TEMP_FILES[@]}"; do
            if [ -f "$file" ]; then
                rm -f "$file"
            fi
        done
        
        log_info "Access points for continued testing:"
        log_info "• OpenSearch: $OPENSEARCH_URL"
        log_info "• Solr Admin: $SOLR_URL/solr/#/$SOLR_CORE"
        log_info "• OpenSearch ecommerce index: $OPENSEARCH_URL/ecommerce/_search"
        log_info "• Search Relevance API: $OPENSEARCH_URL/_plugins/_search_relevance/"
        return 0
    fi

    log_info "Cleaning up..."

    # Clean up OpenSearch resources
    for resource in "${CREATED_RESOURCES[@]}"; do
        local type="${resource%%:*}"
        local id="${resource#*:}"
        
        case "$type" in
            "experiment")
                curl -s -X DELETE "$OPENSEARCH_URL/_plugins/_search_relevance/experiments/$id" > /dev/null 2>&1 || true
                ;;
            "search_config")
                curl -s -X DELETE "$OPENSEARCH_URL/_plugins/_search_relevance/search_configurations/$id" > /dev/null 2>&1 || true
                ;;
            "remote_config")
                curl -s -X DELETE "$OPENSEARCH_URL/_plugins/_search_relevance/remote_search_configurations/$id" > /dev/null 2>&1 || true
                ;;
            "queryset")
                curl -s -X DELETE "$OPENSEARCH_URL/_plugins/_search_relevance/query_sets/$id" > /dev/null 2>&1 || true
                ;;
            "judgment")
                curl -s -X DELETE "$OPENSEARCH_URL/_plugins/_search_relevance/judgments/$id" > /dev/null 2>&1 || true
                ;;
        esac
    done

    # Stop and remove Solr container
    if [ "$SOLR_CONTAINER_STARTED" = true ]; then
        log_info "Stopping Solr container..."
        docker stop solr_demo 2>/dev/null || true
        docker rm solr_demo 2>/dev/null || true
    fi

    # Remove temporary files
    for file in "${TEMP_FILES[@]}"; do
        if [ -f "$file" ]; then
            rm -f "$file"
        fi
    done

    log_success "Cleanup completed"
}

# Main execution
main() {
    # Parse command-line arguments first
    parse_arguments "$@"

    log_section "Remote Query Capability Demonstration"
    log_info "This demo showcases the remote query capability of the OpenSearch Search Relevance plugin"
    log_info "by comparing search performance between OpenSearch and Apache Solr using identical datasets."
    echo
    log_info "This script uses live remote query endpoints to execute Solr searches via the plugin."
    log_info "Responses are normalized using the configured response template for apples-to-apples comparison."
    echo

    # Set up cleanup trap
    trap cleanup EXIT

    # Check dependencies
    log_section "Dependency Check"
    check_dependencies

    if [ "$SKIP_DATA_LOADING" = true ]; then
        log_section "Data Loading Skipped"
        log_info "Skipping data loading steps - assuming OpenSearch and Solr are already set up with data"
        log_info "Verifying that required services and data are available..."
        
        # Basic connectivity checks
        if ! wait_for_service "$OPENSEARCH_URL" "OpenSearch" 5; then
            log_error "OpenSearch is not available at $OPENSEARCH_URL"
            log_info "Start OpenSearch and install the Search Relevance plugin, then re-run this script."
            exit 1
        fi

        if ! wait_for_service "$SOLR_URL/solr/admin/cores" "Solr" 5; then
            log_error "Solr is not available at $SOLR_URL"
            log_info "Start Solr with the '$SOLR_CORE' core, then re-run this script."
            exit 1
        fi

        # Check if search relevance plugin is available
        local plugins_response=$(curl -s "$OPENSEARCH_URL/_cat/plugins")
        if ! echo "$plugins_response" | grep -q "search-relevance"; then
            log_error "Search Relevance plugin is not installed or enabled"
            exit 1
        fi

        # Ensure Workbench is enabled
        local settings_payload='{"persistent":{"plugins.search_relevance.workbench_enabled": true}}'
        curl -s -X PUT "$OPENSEARCH_URL/_cluster/settings" -H "Content-Type: application/json" -d "$settings_payload" > /dev/null 2>&1 || true

        # Require remote REST endpoint support
        if ! remote_endpoints_available; then
            log_error "Remote REST endpoints are not available in the running OpenSearch/plugin instance."
            log_info "Ensure you're running OpenSearch with the Search Relevance plugin that includes remote endpoints and that Workbench is enabled."
            exit 1
        fi

        log_success "Services verified - proceeding with query and comparison testing"
        verify_data_parity
    else
        # Build subset dataset from judgments to speed up ingestion
        download_data_file
        local judgments_file="$SCRIPT_DIR/../resources/judgment/ImportJudgments.json"
        local subset_file
        subset_file=$(build_subset_from_judgments "$judgments_file") || { log_error "Subset build failed"; exit 1; }
        ECOMMERCE_DATA_FILE="$subset_file"
        log_info "Using subset data file: $ECOMMERCE_DATA_FILE"

        # Set up OpenSearch first (required for remote demo)
        log_section "OpenSearch Setup"
        setup_opensearch_data

        # Require remote REST endpoint support
        if ! remote_endpoints_available; then
            log_error "Remote REST endpoints are not available in the running OpenSearch/plugin instance."
            log_info "Ensure you're running OpenSearch with the Search Relevance plugin that includes remote endpoints and that Workbench is enabled."
            exit 1
        fi

        # Start Solr and load data
        log_section "Solr Setup"
        start_solr
        configure_solr_schema

        log_section "Data Loading"
        transform_data_for_solr
        load_data_to_solr "esci_us_solr.json"
        verify_data_parity
    fi

    # Demonstrate template transformations (required)
    log_section "Template Transformation Testing"
    test_query_template
    test_response_template

    # Demonstrate search comparison
    log_section "Search Comparison Demonstration"
    demonstrate_search_comparison

    # Show remote search concept
    log_section "Remote Search Configuration"
    show_remote_search_concept

    # Experiment Integration Demonstration
    log_section "Experiment Integration Demonstration"
    log_info "Now demonstrating the complete integration with Search Relevance Workbench experiments..."
    log_info "This shows how remote search configurations can be used in formal experiments with IR metrics."
    echo

    # Create search configurations for experiment
    log_info "Creating search configurations for experiment comparison..."
    local local_config_id
    local_config_id=$(create_local_search_config)
    
    local remote_config_id
    remote_config_id=$(create_remote_search_config_for_experiment)

    # Create variant remote configuration to ensure measurable differences
    local remote_config_variant_id
    remote_config_variant_id=$(create_remote_search_config_variant)

    # Create query set and judgments
    log_info "Setting up query set and judgments from ESCI data..."
    local queryset_id
    queryset_id=$(create_query_set_from_judgments)
    
    local judgment_id
    judgment_id=$(create_judgment_set)

    # Create and run experiments:
    # - Remote-only (REMOTE_SEARCH_EVALUATION) with remote config id(s)
    # - Local-only (POINTWISE_EVALUATION) with the local search configuration
    log_info "Creating and executing remote and local experiments for comparison..."

    local remote_experiment_id
    remote_experiment_id=$(create_remote_experiment "$remote_config_id" "$queryset_id" "$judgment_id")

    # Variant remote-only experiment
    local remote_variant_experiment_id
    remote_variant_experiment_id=$(create_remote_experiment "$remote_config_variant_id" "$queryset_id" "$judgment_id")

    local local_experiment_id
    local_experiment_id=$(create_local_experiment "$local_config_id" "$queryset_id" "$judgment_id")

    # Create a PAIRWISE_COMPARISON experiment between local and remote to compute comparative IR metrics
    # Note: PAIRWISE_COMPARISON expects Search Configuration IDs for both variants. Remote config ids (like 'solr_remote')
    # are Remote Search Configuration documents and are not valid here. Guard to avoid invalid requests.
    local pairwise_experiment_id=""
    if [[ "$remote_config_id" =~ ^[0-9a-fA-F-]{36}$ ]]; then
        pairwise_experiment_id=$(create_experiment "$local_config_id" "$remote_config_id" "$queryset_id" "$judgment_id")
    else
        log_warning "Skipping PAIRWISE_COMPARISON: remote_config_id '$remote_config_id' is not a Search Configuration id. Using REMOTE_SEARCH_EVALUATION for remote metrics."
    fi

    # Wait for both experiments
    local any_failed=false

    if wait_for_experiment "$remote_experiment_id"; then
        log_section "Remote Experiment Results (Baseline)"
        show_experiment_results "$remote_experiment_id"
        log_section "Remote Metrics (Baseline)"
        show_metrics_comparison "$remote_experiment_id"
        log_section "Remote Persisted Metrics (evaluation_result, Baseline)"
        fetch_and_print_metrics "$remote_experiment_id"
    else
        any_failed=true
        log_warning "Remote experiment (baseline) did not complete successfully - metrics unavailable"
    fi

    if wait_for_experiment "$remote_variant_experiment_id"; then
        log_section "Remote Variant Experiment Results"
        show_experiment_results "$remote_variant_experiment_id"
        log_section "Remote Variant Metrics"
        show_metrics_comparison "$remote_variant_experiment_id"
        log_section "Remote Variant Persisted Metrics (evaluation_result)"
        fetch_and_print_metrics "$remote_variant_experiment_id"
    else
        any_failed=true
        log_warning "Remote variant experiment did not complete successfully - metrics unavailable"
    fi

    if wait_for_experiment "$local_experiment_id"; then
        log_section "Local Experiment Results"
        show_experiment_results "$local_experiment_id"
        log_section "Local Metrics"
        show_metrics_comparison "$local_experiment_id"
        log_section "Local Persisted Metrics (evaluation_result)"
        fetch_and_print_metrics "$local_experiment_id"
    else
        any_failed=true
        log_warning "Local experiment did not complete successfully - metrics unavailable"
    fi

    # Also wait for the pairwise experiment and display its results/metrics (only if we created one)
    if [ -n "$pairwise_experiment_id" ]; then
        if wait_for_experiment "$pairwise_experiment_id"; then
            log_section "Pairwise Experiment Results"
            show_experiment_results "$pairwise_experiment_id"
            log_section "Pairwise Metrics"
            show_metrics_comparison "$pairwise_experiment_id"
            log_section "Pairwise Persisted Metrics (evaluation_result)"
            fetch_and_print_metrics "$pairwise_experiment_id"
        else
            any_failed=true
            log_warning "Pairwise experiment did not complete successfully - metrics unavailable"
        fi
    else
        log_info "Pairwise experiment skipped; REMOTE_SEARCH_EVALUATION and POINTWISE_EVALUATION were executed independently."
    fi

    # Simple comparison guidance (requires both to have completed)
    if [ "$any_failed" = false ]; then
        log_section "Aggregated Metrics Comparison (Remote vs Local)"
        show_side_by_side_metrics "$remote_experiment_id" "$local_experiment_id"
        log_section "Remote vs Remote Variant Metrics"
        show_side_by_side_metrics "$remote_experiment_id" "$remote_variant_experiment_id"

        log_section "Comparison Summary"
        if [ -n "$pairwise_experiment_id" ]; then
            log_info "Primary metrics are available in the Pairwise experiment: $pairwise_experiment_id."
            log_info "Use NDCG/MAP/MRR/Precision@K from the pairwise results to determine which configuration performs better on this query set."
        else
            log_info "Pairwise metrics not available (skipped). Review REMOTE_SEARCH_EVALUATION and POINTWISE_EVALUATION outputs above, or create two local Search Configurations to run a PAIRWISE_COMPARISON."
        fi
    else
        log_section "Comparison Summary"
        log_warning "Could not produce a complete comparison because one or both experiments did not complete."
    fi
    
    echo
    log_info "Access points for further exploration:"
    log_info "• OpenSearch: $OPENSEARCH_URL"
    log_info "• Solr Admin: $SOLR_URL/solr/#/$SOLR_CORE"
    log_info "• OpenSearch ecommerce index: $OPENSEARCH_URL/ecommerce/_search"
    log_info "• Search Relevance API: $OPENSEARCH_URL/_plugins/_search_relevance/"
    if [ -n "${experiment_id:-}" ]; then
        log_info "• Experiment details: $OPENSEARCH_URL/_plugins/_search_relevance/experiments/$experiment_id"
    fi
    echo
    
    if [ "$SKIP_CLEANUP" = true ]; then
        log_info "Demo completed. All resources preserved for continued exploration."
        log_info "Run the script again with --skip-data-loading to quickly test different configurations."
    else
        log_info "Demo completed. Resources will be cleaned up on exit."
    fi
}

# Run main function
main "$@"
