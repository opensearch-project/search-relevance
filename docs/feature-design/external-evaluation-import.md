# External Evaluation Import Feature Design

> **Target Audience**: Development teams building features and enhancements for the search-relevance plugin.

## Introduction

This document outlines the design and implementation of the External Evaluation Import capability for the OpenSearch Search Relevance plugin. This feature enables users to import pre-computed evaluation results from external pipelines into the Search Relevance Workbench (SRW) using the existing PostExperiment API in a simplified import-only mode.

## Problem Statement

Users with external evaluation pipelines need a way to import their pre-computed evaluation results into SRW for unified analysis and reporting. Currently, the PostExperiment API supports running new evaluations but lacks a dedicated mechanism for importing results that have already been computed externally.

**Key Problem Addressed:**
- **External Pipeline Integration**: Users have external evaluation pipelines that compute metrics outside of OpenSearch and need to bring those results into SRW for analysis and comparison

**Impact of Not Implementing:**
- Users cannot leverage SRW's analysis and reporting capabilities for externally computed evaluation results
- Fragmented evaluation workflows where external results remain isolated from SRW

**Primary Users/Stakeholders:**
- Users with existing external evaluation pipelines
- Teams that compute evaluation metrics outside of OpenSearch
- Organizations wanting to centralize evaluation results in SRW

## Use Cases

### Required Use Cases
1. **External Pipeline Result Import** - Import pre-computed evaluation results from external evaluation pipelines using the PostExperiment API with pre-computed evaluation data

### Nice-to-Have Use Cases
1. **Bulk Import Support** - Efficiently import large numbers of evaluation results in a single request
2. **Validation and Error Reporting** - Validate imported data and provide clear error messages for malformed requests

## Requirements

### Functional Requirements

1. **Simplified Import Support**
   - Support for importing pre-computed evaluation results via PostExperiment API
   - Accept pre-computed evaluation results with metrics already calculated
   - Store imported results using existing EvaluationResult infrastructure
   - Create experiments with COMPLETED status immediately

2. **Data Format Support**
   - Accept evaluation results in the existing PostExperiment JSON format
   - Support pre-computed metrics in the evaluationResultList field, including ones not calculated by SRW natively
   - Maintain compatibility with existing query sets, search configurations, and judgments JSON formats

3. **Integration with Existing Infrastructure**
   - Use existing EvaluationResult storage and retrieval mechanisms
   - Integrate with current experiment management and reporting
   - Support association with existing query sets and search configurations

### Non-Functional Requirements

1. **Performance**
   - Handle multiple evaluation results in a single request efficiently
   - Synchronous processing for immediate completion
   - Minimal impact on existing PostExperiment functionality

2. **Reliability**
   - Validate imported data for consistency and completeness
   - Provide clear error messages for invalid requests
   - Maintain data integrity during import operations
   - Failed imports marked as ERRORED status

## Out of Scope

1. **Format Conversion** - Converting between different evaluation result formats (TREC, ESCI, etc.)
2. **File Upload Support** - Direct file upload capabilities (users provide data in request body)
3. **Advanced Validation** - Complex cross-validation between imported results and existing data
4. **Real-time Evaluation** - This feature only imports pre-computed results, not execute new evaluations
5. **Conditional Processing** - No experiment type-based routing; all requests are processed as imports

## Current State

The OpenSearch Search Relevance plugin currently provides:
- PostExperiment API for creating and executing experiments
- Support for experiment types: `POINTWISE_EVALUATION`, `PAIRWISE_COMPARISON`, `HYBRID_OPTIMIZER`
- EvaluationResult storage and management
- Integration with query sets, search configurations, and judgments

**Current Enhancement:**
- PostExperiment API has been modified to support import-only mode
- All requests with evaluationResultList are processed as imports
- Experiments are created with COMPLETED status immediately

**Components that have been enhanced:**
- `PostExperimentTransportAction` - Focused action to only import evaluation results
- Request validation - Ensures imported data is valid and complete
- Synchronous processing - Results are stored immediately

## Solution Overview

The solution modifies the existing PostExperiment API to operate in an import-only mode when evaluationResultList is provided, allowing users to import pre-computed evaluation results without executing new evaluations.

**Key Technologies and Dependencies:**
- Existing OpenSearch Search Relevance plugin infrastructure
- Current PostExperiment API and data models
- Existing EvaluationResult storage mechanisms

**Integration with OpenSearch Core:**
- Uses existing OpenSearch indexing for storing imported results
- Leverages current security and authorization mechanisms
- Integrates with existing monitoring and logging

**Interaction with Existing Search-Relevance Features:**
- Uses existing PostExperiment API with simplified processing
- Uses existing EvaluationResult data model and storage
- Integrates with current experiment management and reporting
- Maintains compatibility with existing query sets and search configurations

## Solution Design

### Proposed Solution

The solution simplifies the PostExperiment API to operate in import-only mode, accepting pre-computed evaluation results and storing them using the existing infrastructure with immediate completion.

#### Enhanced PostExperiment API

**Request Format:**
```json
{
  "type": "POINTWISE_EVALUATION",
  "querySetId": "1234-queryset-id-5678",
  "searchConfigurationList": ["abcd-searchConfiguration-defg"],
  "judgmentList": ["hjkl-judgements-id-asdf"],
  "size": 5,
  "evaluationResultList": [
    {
      "searchText": "led tv",
      "documentIds": [
        "B079VXT54Z",
        "B07MXBCQCF",
        "B07ZFBTFQF",
        "B0915F456C",
        "B07176GBXQ"
      ],
      "metrics": [
        {
          "metric": "dcg@10",
          "value": 0.8
        },
        {
          "metric": "precision@5",
          "value": 0.6
        }
      ]
    }
  ]
}
```

#### Core Components

**1. PostExperimentTransportAction Simplification**
- Always processes requests as import operations
- Skips evaluation execution logic entirely
- Creates experiments with COMPLETED status immediately

**2. Synchronous Processing**
- Validates and stores evaluation results synchronously
- Creates experiment with COMPLETED status
- Returns response immediately upon completion

**3. Request Validation**
- Ensures evaluationResultList is provided and non-empty
- Validates that exactly one search configuration is provided
- Validates that exactly one judgment list is provided
- Verifies consistency with referenced query sets and search configurations

#### Processing Flow

1. **Request Validation**: Validate that request contains required fields and exactly one search configuration and judgment list
2. **Data Processing**: Process each evaluation result in the evaluationResultList
3. **Result Storage**: Store evaluation results using existing EvaluationResult infrastructure synchronously
4. **Experiment Creation**: Create experiment with COMPLETED status and result summaries
5. **Response Generation**: Return experiment ID and success confirmation

### Alternative Solutions Considered

**Alternative 1: Conditional Processing Based on Experiment Type**
- **Approach**: Add new experiment type and conditional logic to detect import vs. evaluation
- **Pros**: Clear distinction between import and evaluation operations
- **Cons**: Additional complexity, new enum values, conditional logic
- **Decision**: Rejected in favor of simplified always-import approach

**Alternative 2: Separate Import API**
- **Approach**: Create a dedicated API endpoint for importing evaluation results
- **Pros**: Clear separation of concerns, optimized for import operations
- **Cons**: Additional API surface, potential duplication of logic
- **Decision**: Rejected in favor of extending existing PostExperiment API for consistency

### Key Design Decisions

**1. Simplified Always-Import Approach**
- **Rationale**: Simplify implementation by always treating requests as imports when evaluationResultList is provided
- **Trade-off**: Less flexibility vs. simpler implementation and maintenance
- **Impact**: Users can reliably import results without worrying about experiment types

**2. Synchronous Processing**
- **Rationale**: Import operations are typically fast and benefit from immediate completion
- **Trade-off**: Potential blocking vs. immediate feedback and simpler error handling
- **Impact**: Users get immediate confirmation of import success or failure

**3. Reuse Existing Data Models**
- **Rationale**: Use existing EvaluationResult model to ensure compatibility with reporting and analysis
- **Trade-off**: Limited flexibility vs. consistency and compatibility
- **Impact**: Imported results integrate seamlessly with existing functionality

## Technical Specifications

### Data Models

**ExperimentType (unchanged):**
```java
public enum ExperimentType {
    PAIRWISE_COMPARISON,
    POINTWISE_EVALUATION,
    HYBRID_OPTIMIZER
}
```

**PostExperimentRequest (unchanged):**
- Existing request structure supports import operations
- evaluationResultList field used for providing pre-computed results
- All existing validation and processing logic remains intact

**EvaluationResult (unchanged):**
- Existing data model used for storing imported results
- No changes required to support imported data
- Maintains compatibility with existing reporting and analysis

### API Specifications

**PostExperiment API (Import Mode):**

```http
PUT /_plugins/_search_relevance/experiments
Content-Type: application/json

{
  "type": "POINTWISE_EVALUATION",
  "querySetId": "query_set_id",
  "searchConfigurationList": ["config_id"],
  "judgmentList": ["judgment_id"],
  "size": 5,
  "evaluationResultList": [
    {
      "searchText": "search query",
      "documentIds": ["doc1", "doc2", "doc3"],
      "metrics": [
        {
          "metric": "dcg@10",
          "value": 0.8
        },
        {
          "metric": "precision@5",
          "value": 0.6
        }
      ]
    }
  ]
}
```

**Response (unchanged):**
```json
{
  "experimentId": "generated_experiment_id",
  "message": "Experiment created successfully"
}
```

### Implementation Changes

**1. PostExperimentTransportAction.java**
- Simplified to always process as import when evaluationResultList is provided
- Removed conditional logic for different experiment types
- Added synchronous processing for immediate completion
- Enhanced validation for import-specific requirements

**2. Request Validation**
- Ensure evaluationResultList is provided for all operations
- Validate exactly one search configuration and judgment list
- Verify references to query sets and search configurations exist

**3. Synchronous Processing**
- Store evaluation results synchronously using putEvaluationResultSync
- Create experiment with COMPLETED status immediately
- Return response upon successful completion

## Backward Compatibility

### Breaking Changes and Migration Strategy

**No Breaking Changes:**
- All existing PostExperiment API functionality works with the new import-focused approach
- Current experiment types continue to work without modification
- Existing data models and storage mechanisms unaffected

**Behavioral Changes:**
- PostExperiment API now operates in import-only mode when evaluationResultList is provided
- All experiments are created with COMPLETED status immediately
- No asynchronous evaluation execution occurs

### Plugin Upgrade Considerations

**Upgrade Path:**
1. Install updated plugin version
2. Import capability available immediately
3. All requests with evaluationResultList processed as imports
4. Existing functionality unaffected

**Rollback Support:**
- Plugin can be downgraded without data loss
- Imported experiments stored using existing data structures
- Core functionality independent of import enhancements

## Security Considerations

### Security Overview

The External Evaluation Import feature uses the existing PostExperiment API security mechanisms and does not introduce new security concerns beyond the current implementation.

**Security Context:**
- Uses existing authentication and authorization for PostExperiment API
- Imported data validated using existing request validation mechanisms
- No additional sensitive data handling beyond current capabilities

**Existing Security Measures:**
- OpenSearch security plugin integration for authentication
- Role-based access control for experiment operations
- Input validation and sanitization for request data
- Audit logging for all experiment operations

### Additional Considerations

**Input Validation:**
- Validate imported metrics are properly formatted
- Ensure referenced IDs exist and are accessible to the user
- Prevent injection attacks through proper input sanitization

**Resource Protection:**
- Use existing rate limiting for PostExperiment API
- Apply existing resource quotas for experiment operations
- Monitor for unusually large import requests

## Testing Strategy

### Unit Testing

**New Test Cases:**
- PostExperimentTransportAction handles import operations correctly
- Request validation for import operations
- Error handling for malformed import requests
- Synchronous processing and immediate completion

**Enhanced Existing Tests:**
- PostExperimentTransportActionTests updated to cover import scenarios
- Integration tests for end-to-end import workflows
- Validation tests for import-specific requirements

### Integration Testing

**Import Workflow Testing:**
- End-to-end import of evaluation results
- Integration with existing experiment management
- Compatibility with existing reporting and analysis
- Error handling and recovery scenarios

### Compatibility Testing

**API Compatibility:**
- PostExperiment functionality works in import-only mode
- Backward compatibility with existing request formats
- Integration with existing query sets and search configurations

## Performance Considerations

### Performance Impact

**Improved Performance for Import Use Cases:**
- Synchronous processing eliminates async overhead for import operations
- No evaluation execution reduces computational requirements
- Leverages existing bulk indexing capabilities for multiple results

**Resource Utilization:**
- Memory usage similar to existing PostExperiment operations
- Storage requirements identical to executed experiments
- Network overhead limited to request/response data

### Optimization Opportunities

**Bulk Processing:**
- Process multiple evaluation results efficiently in single request
- Use existing bulk indexing for storing multiple results
- Minimize validation overhead through batch processing

## Additional Resources

- [OpenSearch Search Relevance Plugin Documentation](https://opensearch.org/docs/latest/search-plugins/search-relevance/)
- [PostExperiment API Documentation](../api/post-experiment.md)
- [Contributing Guidelines](../CONTRIBUTING.md)
- [Plugin Development Guide](https://opensearch.org/docs/latest/developers/plugins/)
