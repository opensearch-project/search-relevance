#!/usr/bin/env python3
"""
Generate Pairwise Experiment JSON for Search Relevance Workbench

This script takes two Quepid export files and generates a pairwise experiment file
that can be imported into OpenSearch Search Relevance Workbench.

The script:
1. Reads two Quepid export files
2. Extracts document IDs for each query
3. Simulates differences between two search configurations
4. Calculates comparison metrics
5. Generates a pairwise experiment JSON file

Example:
    python generate_pairwise_experiment.py --mod-ratio 0.4 --max-docs 10

Author: Eric Pugh
"""

import json
import os
import random
import argparse
from pathlib import Path

# Constants - these will be replaced later by the script that imports the experiment
QUERY_SET_ID = "placeholder-query-set-id"
SEARCH_CONFIG_ID_1 = "searchconfig-1-guid"
SEARCH_CONFIG_ID_2 = "searchconfig-2-guid"

# Define default file paths relative to script location
SCRIPT_DIR = Path(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_INPUT_FILE_1 = SCRIPT_DIR / "Movie_Search_Quepid_1.json"
DEFAULT_INPUT_FILE_2 = SCRIPT_DIR / "Movie_Search_Quepid_2.json"
DEFAULT_OUTPUT_FILE = SCRIPT_DIR / "movies_experiment_pairwise.json"

def extract_doc_ids_by_query(data):
    """Extract document IDs for each query from the Quepid data.
    
    Args:
        data: Loaded JSON data from a Quepid export file
        
    Returns:
        A dictionary mapping query text to a list of document IDs
    """
    query_to_docs = {}
    
    for query_item in data.get("queries", []):
        query_text = query_item.get("query_text", "")
        if not query_text:  # Skip queries with no text
            continue
            
        doc_ids = []
        
        # Extract doc_ids from the ratings list
        for doc in query_item.get("ratings", []):
            doc_id = doc.get("doc_id", "")
            if doc_id:  # Skip empty doc IDs
                doc_ids.append(doc_id)
            
        if doc_ids:  # Only add queries with at least one document
            query_to_docs[query_text] = doc_ids
    
    return query_to_docs

def calculate_metrics(docs1, docs2):
    """Calculate comparison metrics between two document lists.
    
    This calculates Jaccard similarity (exact overlap) and approximates
    other rank-aware metrics for illustrative purposes.
    
    Args:
        docs1: First list of document IDs
        docs2: Second list of document IDs
        
    Returns:
        A list of metric objects with metric name and value
    """
    # Calculate Jaccard similarity (intersection over union)
    set1 = set(docs1)
    set2 = set(docs2)
    intersection = len(set1.intersection(set2))
    union = len(set1.union(set2))
    jaccard = round(intersection / union if union > 0 else 0, 2)
    
    # Position-aware overlap calculation (simplified)
    # This gives more weight to matches at the top of the list
    position_weight_sum = 0
    position_weight_max = 0
    
    for i, doc_id in enumerate(docs1):
        weight = 1.0 / (i + 1)  # Position weight (1/1, 1/2, 1/3, etc.)
        position_weight_max += weight
        
        if doc_id in docs2:
            # Find position in second list
            j = docs2.index(doc_id)
            # Discount by position difference
            position_discount = 1.0 / (abs(i - j) + 1)
            position_weight_sum += weight * position_discount
    
    # Normalize score
    position_score = round(position_weight_sum / position_weight_max if position_weight_max > 0 else 0, 2)
    
    # Return a list of metrics
    return [
        {"metric": "jaccard", "value": jaccard},
        {"metric": "rbo50", "value": round(position_score * 0.7, 2)},
        {"metric": "rbo90", "value": round((jaccard + position_score) / 2, 2)},
        {"metric": "frequencyWeighted", "value": round(min(position_score + 0.2, 1.0), 2)}
    ]

def modify_doc_ids_for_second_snapshot(docs, modification_ratio=0.3):
    """Create a slightly different document list for the second snapshot.
    
    This simulates differences between two search configurations by replacing
    a portion of the document IDs with new random IDs.
    
    Args:
        docs: Original document ID list
        modification_ratio: Percentage of documents to modify (0.0-1.0)
    
    Returns:
        A modified list of document IDs
    """
    # Create a copy to avoid modifying the original
    modified_docs = docs.copy()
    
    # If empty list or invalid ratio, return as is
    if not docs or modification_ratio <= 0 or modification_ratio > 1.0:
        return modified_docs
    
    # Calculate how many docs to modify
    num_to_modify = max(1, int(len(docs) * modification_ratio))
    
    # Generate some "new" document IDs not in the original list
    # In a real scenario, these would come from the second search configuration
    new_doc_ids = [str(random.randint(100000, 999999)) for _ in range(num_to_modify)]
    
    # Replace some documents in the list
    positions_to_replace = random.sample(range(len(modified_docs)), num_to_modify)
    for i, pos in enumerate(positions_to_replace):
        modified_docs[pos] = new_doc_ids[i]
    
    return modified_docs

def parse_arguments():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description='Generate a pairwise experiment JSON file from Quepid export files.'
    )
    parser.add_argument(
        '--input1',
        type=str,
        default=str(DEFAULT_INPUT_FILE_1),
        help=f'First input file path (default: {DEFAULT_INPUT_FILE_1})'
    )
    parser.add_argument(
        '--input2',
        type=str,
        default=str(DEFAULT_INPUT_FILE_2),
        help=f'Second input file path (default: {DEFAULT_INPUT_FILE_2})'
    )
    parser.add_argument(
        '--output',
        type=str,
        default=str(DEFAULT_OUTPUT_FILE),
        help=f'Output file path (default: {DEFAULT_OUTPUT_FILE})'
    )
    parser.add_argument(
        '--max-docs',
        type=int,
        default=10,
        help='Maximum number of documents to include per query (default: 10)'
    )
    parser.add_argument(
        '--mod-ratio',
        type=float,
        default=0.3,
        help='Modification ratio for second snapshot (0.0-1.0, default: 0.3)'
    )
    parser.add_argument(
        '--random-seed',
        type=int,
        default=42,
        help='Random seed for reproducible results (default: 42)'
    )
    
    return parser.parse_args()

def main():
    """Main entry point for the script."""
    # Parse command line arguments
    args = parse_arguments()
    
    # Set random seed for reproducibility
    random.seed(args.random_seed)
    
    print(f"Processing with modification ratio: {args.mod_ratio}, max docs: {args.max_docs}")
    print(f"Input files: {args.input1}, {args.input2}")
    print(f"Output file: {args.output}")
    
    # Load the input files
    try:
        with open(args.input1, "r") as f1:
            data_1 = json.load(f1)
            print(f"Successfully loaded {args.input1}")
        
        with open(args.input2, "r") as f2:
            data_2 = json.load(f2)
            print(f"Successfully loaded {args.input2}")
    except Exception as e:
        print(f"Error loading input files: {e}")
        return
    
    # Extract document IDs for each query from both input files
    # Limit to max_docs per query
    def extract_limited_docs(data):
        return {k: v[:args.max_docs] for k, v in extract_doc_ids_by_query(data).items()}
    
    queries_docs_1 = extract_limited_docs(data_1)
    queries_docs_2 = extract_limited_docs(data_2)
    
    # Find common queries between the two files
    common_queries = set(queries_docs_1.keys()).intersection(set(queries_docs_2.keys()))
    print(f"Found {len(common_queries)} common queries")
    
    # Create the results array for the output
    results = []
    
    for query in common_queries:
        # Get document IDs for this query from the first configuration
        docs_1 = queries_docs_1[query]
        
        # For the second configuration, create a modified version
        # In a real scenario, these would come directly from the second configuration
        docs_2 = modify_doc_ids_for_second_snapshot(docs_1, args.mod_ratio)
        
        # Create a snapshot for each search configuration
        snapshot_1 = {
            "searchConfigurationId": SEARCH_CONFIG_ID_1,
            "docIds": docs_1
        }
        
        snapshot_2 = {
            "searchConfigurationId": SEARCH_CONFIG_ID_2,
            "docIds": docs_2
        }
        
        # Create the result object for this query
        result = {
            "snapshots": [snapshot_1, snapshot_2],
            "queryText": query,
            "metrics": calculate_metrics(docs_1, docs_2)
        }
        
        results.append(result)
    
    # Create the final output structure
    output_data = {
        "querySetId": QUERY_SET_ID,
        "searchConfigurationList": [SEARCH_CONFIG_ID_1, SEARCH_CONFIG_ID_2],
        "type": "PAIRWISE_COMPARISON",
        "size": 10,
        "results": results
    }
    
    # Write the output to file
    try:
        with open(args.output, "w") as f:
            json.dump(output_data, f, indent=2)
        print(f"Successfully created {args.output}")
    except Exception as e:
        print(f"Error writing output file: {e}")
        return

if __name__ == "__main__":
    main()
