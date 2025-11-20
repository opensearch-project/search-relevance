# Regularly Scheduled Experiments Feature Design

## Introduction

This document outlines the steps to make regularly scheduled search relevance evaluations possible for the OpenSearch Search Relevance Plugin. 

## Problem Statement

Currently, there is not a clean way to rerun search evaluations, given that the data that the search is evaluated on can change overtime. 

**Key Problems:**
- Users would have to rerun experiments manually to detect changes in the search quality
- Experiment results might not be consistent because the search algorithms and data can change and affect search quality.

**Impact of Not Implementing:**
- Users would not be able to automate a schedule for running evaluations
- An alerting or notification system would not be possible to implement without automated evaluation runs

**Primary Stakeholders**
- Teams trying to track search quality over time
- Users tracking critical search metrics and ensuring they stay above a threshold
- Teams trying to compare the impact of using one search algorithm over another
- Users comparing metrics when migrating from a different search engine

## Use Cases

### Required Use Cases
1. **Rerunning a experiment** After running an experiment through the PutExperiment API, that experiment can be rerun given the `experimentId` and a `cronExpression`
2. **Search Quality Tracking** The user could graph and analyze the search quality overtime based on past experiments
3. **Alerting Based on Threshold** Make sure that if the search quality drops below a certain threshold, the user could be notified automatically.
4. **Searching Previous Experiments** The user could look for an experiment run at a given time, and the experiments run around that time are returned.

### Nice-to-Have Use Cases
1. **Resource Usage Tracking** Make sure that when jobs are submitted, the amount of resources that is used can be tracked and budgetted.
2. **Input Validation** When a request to schedule a regularly-running experiment is submitted, the `experimentId` and `cronExpression` should be validated early.
3. **Concurrency Management** The same experiment should not be started when that experiment is still in progress from the interval.

## Requirements

### Functional Requirements

1. **Experiment Scheduling API** There should be a separate API to POST, GET, and DELETE these running, scheduled experiments.

2. **Evaluation Workflows** The current workflow for running an experiment should be reused so that each time an experiment is rerun, the same steps for that workflow are reused.

### Non-Functional Requirements

1. **Rate and Interval Limiting** The minimum interval between search evaluation reruns should be set so that the system does not become overwhelmed.

2. **Extensibility for experiment API changes** There should be a centralized location to run the experiment workflow so that if that changes, the changes will propate to the workflow when rerunning experiments.

3. **Performance** The thread pool allows multiple tasks to run concurrently, therefore, this should be maintained for regularly scheduled experiments.

## Out of Scope

1. **Resource monitoring** Existing workloads should have their resources tracked to measure how much of the system is being used for job scheduling.

2. **Alerting** We should be able to send the users notification when a metric dips below a certain threshold.

## Current State

Currently, the Search Relevance Workbench currently has a feature to submit `POINTWISE_EVALUATION`, `PAIRWISE_COMPARISON`, `HYBRID_OPTIMIZER` experiments through the `PutExperimentAction` api. 

**Components that need to change**
- `SearchRelevancePlugin` we need to implement the extension point of the `job-scheduler` plugin, and also register the new actions and rest handlers for adding, getting, and deleting jobs.

## Solution Overview

The solution will implement a custom job runner, job parameters, actions, rest handlers, and extend the `job-scheduler` plugin. 

**Key technologies and dependencies**
- Existing OpenSearch Search Relevance plugin infrastructure
- `job-scheduler` plugin infrastructure and extension points
- Cron job scheduler format from `job-scheduler` will be reused.

**Integration with OpenSearch core**
- Uses action and rest handlers from OpenSearch
- The job scheduler uses listeners for index changes in the job index that is being created for storing jobs

**Interaction with existing search-relevance features**
- The search evaluation should be reused for running the regularly scheduled evaluations
- The data access objects and index access techniques from search-relevance should be reused

## Solution Design

### Proposed Solution

There should be 3 new APIS, GET, POST, DELETE. Additionally, the job runner and job parameters should be extended to accommodate for our specific use cases. 

### Alternative Solutions Considered

**Alternative 1: Adding the scheduled parameter inside the already-existing PutExperiment API**
- **Approach**: Would add a parameter that denotes the cron schedule for rerunning the job and also index the job into the job index to enable scheduling when the experiment finished
- **Pros**: Relatively small change which leverages an existing api
- **Cons**: Less user friendly as the user must decide on the schedule when creating an experiment. 
- **Decision**: Rejected in favor of creating a new API

### Key Design Decisions

**TODO**: Summarize critical decisions:
- Technology choices and rationale
- Trade-offs made
- Impact on existing functionality

**Technology choices and rationale**
- Extended an already existing `job-scheduler` plugin to schedule jobs because it already set up the index listeners, scheduling formats, and concurrency management frameworks. 

**Trade-offs made**
- Limited mostly to the extension framework of `job-scheduler`, but most of the necessary features are already part of the plugin.

**Impact**
- Users can automate the process of rerunning search experiments

## Technical Specifications

### Data models
**New index for scheduled jobs:**

```json
{
  "properties": {
    "id": { "type": "keyword" },
    "enabled": {
      "type": "boolean"
    },
    "enabledTime": {
      "type": "long"
    },
    "lastUpdateTime": {
      "type": "long"
    },
    "schedule": {
      "properties": {
        "cron": {
          "properties": {
            "expression": {
              "type": "keyword"
            },
            "timezone": {
              "type": "keyword"
            }
          }
        }
      }
    }
  }
}
```

**New index for scheduled experiment results**

```json
{
  "properties": {
    "id": { "type": "keyword" },
    "experimentId": { "type": "keyword" },
    "timestamp": {
      "type": "date",
      "format": "strict_date_time"
    },
    "status": { "type": "keyword" },
    "results": { "type": "object"}
  }
}
```

**Updating index for evaluation results**

```json
{
  "properties": {
    "id": { "type": "keyword" },
    "timestamp": { "type": "date", "format": "strict_date_time" },
    "searchConfigurationId": { "type": "keyword" },
    "experimentId": { "type": "keyword" },
    "experimentVariantId": { "type": "keyword" },
    "experimentVariantParameters": { "type": "keyword" },
    "scheduledRunId": {"type": "keyword"},
    "searchText": { "type": "keyword" },
    "judgmentList": { "type": "keyword" },
    "documentIds": { "type": "keyword" },
    "metrics": {
      "type": "nested",
      "properties": {
        "metric": { "type": "keyword" },
        "value": { "type": "float" }
      }
    }
  }
}
```

**Job parameters schema**

```
private String jobName;
private Instant lastUpdateTime;
private Instant enabledTime;
private boolean isEnabled;
private Schedule schedule;
private String indexToWatch;
private Long lockDurationSeconds;
private Double jitter;
private String experimentId;
```

### API Specification

```http
POST /_plugins/_search_relevance/experiments/schedule
{
    "experimentId": "2bb07ecb-082a-4bab-b9c0-dc225e5c35ae",
    "cronExpression": "* * * * *"
}
```
{% include copy-curl.html %}

**Response:**
```json
{
  "job_id": "2bb07ecb-082a-4bab-b9c0-dc225e5c35ae",
  "job_result": "CREATED"
}
```

```http
GET /_plugins/_search_relevance/experiments/schedule/2bb07ecb-082a-4bab-b9c0-dc225e5c35ae
```

**Response:**
```json
{
  "id": "2bb07ecb-082a-4bab-b9c0-dc225e5c35ae",
  "enabled": true,
  "enabled_time": 1755642736969,
  "experiment_id": "2bb07ecb-082a-4bab-b9c0-dc225e5c35ae",
  "index_name_to_watch": "index",
  "last_update_time": 1755642737029,
  "lock_duration_seconds": 20,
  "name": "name",
  "schedule": {
    "cron": {
      "expression": "* 1 * * *",
      "timezone": "UTC"
    }
  }
}
```

```http
DELETE /_plugins/_search_relevance/experiments/schedule/2bb07ecb-082a-4bab-b9c0-dc225e5c35ae
```
{% include copy-curl.html %}

**Response**
```json
{
  "_index": ".search-relevance-scheduled-experiment-jobs",
  "_id": "2bb07ecb-082a-4bab-b9c0-dc225e5c35ae",
  "_version": 2,
  "result": "deleted",
  "forced_refresh": true,
  "_shards": {
    "total": 2,
    "successful": 1,
    "failed": 0
  },
  "_seq_no": 1,
  "_primary_term": 1
}
```

### Request flows
**Adding a job:**
```mermaid
flowchart LR
    PostScheduledExperimentTransportAction[PostScheduledExperimentTransportAction] -->|add job to index| ScheduledJobsDao(ScheduledJobsDao)
    ScheduledJobsDao --> |Access job index to
    add the job and invoke 
    job runner|JobIndex(Job index)
```

**Getting a job:**
```mermaid
flowchart LR
    GetScheduledExperimentTransportAction[GetScheduledExperimentTransportAction] -->|request for job with
    specific job id| ScheduledJobsDao(ScheduledJobsDao)
    ScheduledJobsDao --> |get job with job id 
    from index |JobIndex(Job index)
```

**Deleting a job:**
```mermaid
flowchart LR
    DeleteScheduledExperimentTransportAction[DeleteScheduledExperimentTransportAction] -->|request to delete job with
    specific job id| ScheduledJobsDao(ScheduledJobsDao)
    ScheduledJobsDao --> |delete job with job id 
    from index |JobIndex(Job index)
```

### Implementation Changes

1. **Request Validation**
- `experimentId` and `cronExpression` should be validated and checked early

2. **ScheduledJobsDao.java**
- Creates, retrieves, updates, and deletes jobs in the job index
- Triggers job runner when creating or updating jobs

3. **PostScheduledExperimentTransportAction.java**
- Handles adding the new job into the new job index
- Triggers the job runner on indexing

4. **SearchRelevanceJobRunner.java**
- Runs the logic for calculating an experiment.
- Takes in job parameters and encapsulates all the logic for running the experiment in a runnable.


## Backward Compatibility

- New APIs are added which means that the specification should be updated.
- The index `search-relevance-evaluation-result` is updated so that there is an additional field to indicate whether the result was from a scheduled or non-scheduled experiment
- Mapping strategy: As a new mapping update strategy, there should be a version manager for tracking the schema changes of an index mapping over time. However, this will be worked on in a future issue for all search relevant indices. 


## Security Considerations

### Attack Vectors

- At the moment, job scheduler has a feature to use [role injections](https://github.com/opensearch-project/common-utils/blob/main/src/main/java/org/opensearch/commons/InjectSecurity.java#L170-L188) where the access settings of the user are copied into the job scheduler at runtime, preventing the job scheduler from accessing resources outside of the user's permission level. 
- Currently jobs are not authenticated as well as API requests, but there is work to create a [token-based authentication](https://github.com/opensearch-project/security/issues/5052) system which grants only the permissions needed for the job to run. 

### Additional Considerations
- **Input validations:** Make sure that the inputs for the requests are formatted properly
- **Resource management:** Add settings to ensure that the amount of jobs being scheduled and the interval between job runs is limited

## Testing Strategy

### Unit testing
- Make sure that the new transport actions (`PostScheduledExperimentTransportAction.java`, `GetScheduledExperimentTransportAction.java`, `DeleteScheduledExperimentTransportAction.java`) are covered for interacting witht he job index
- Test `ScheduledJobsDao.java` and confirm that it in fact accesses the undelying jobs index.

### Integration testing
- Try to perform requests on the new rest apis and ensure that the results are as expected through end to end testing
- Error handling and request input validation
- Test limits on number of scheduled jobs in the index and interval spacing

### Performance testing
- Test performance of apis when the number of scheduled jobs are maxed out compared to when there are no scheduled jobs and ensure there is no significant performance degradation

### Compatibility testing
- Integration with existing experiments
- All other rest apis work as expected since there should be no changes to prior apis. 

## Performance and Benchmarking

- If we end up keeping track of all experiment runs, memory should not exceed limits.
- The number of threads given to job scheduling should be limited and at a one-to-one correspondence with the number of scheduled jobs

## Additional Resources

- [OpenSearch RFC Process](https://github.com/opensearch-project/OpenSearch/blob/main/DEVELOPER_GUIDE.md#submitting-changes)
- [Plugin Development Guide](https://opensearch.org/docs/latest/developers/plugins/)
- [Contributing Guidelines](../../CONTRIBUTING.md)
