# Backward Compatibility (BWC) Tests for Search Relevance Plugin

This directory contains BWC (Backward Compatibility) tests for the OpenSearch Search Relevance plugin. These tests ensure that the plugin maintains compatibility during rolling upgrades from older versions to newer versions.

## Overview

BWC tests validate that:
1. **OLD cluster**: Resources created with old plugin versions continue to work
2. **MIXED cluster**: During rolling upgrades, both old and new nodes can process requests
3. **UPGRADED cluster**: New features work while maintaining backward compatibility with old data formats

## Test Structure

```
qa/
├── build.gradle                           # Main QA build configuration
├── rolling-upgrade/                       # Rolling upgrade BWC tests
│   ├── build.gradle                      # Rolling upgrade test configuration
│   └── src/test/java/org/opensearch/searchrelevance/bwc/rolling/
│       ├── AbstractSearchRelevanceRollingUpgradeTestCase.java  # Base class for BWC tests
│       └── LlmJudgmentBWCIT.java         # LLM Judgment BWC integration test
└── README.md                             # This file
```

## Key BWC Scenarios for LLM Judgment

### Old Format (Pre-custom fields)
```json
{
  "querySetQueries": [
    {
      "queryText": "What is OpenSearch?",
      "referenceAnswer": "OpenSearch is a search and analytics suite"
    }
  ]
}
```

### New Format (With custom fields)
```json
{
  "querySetQueries": [
    {
      "queryText": "What is OpenSearch?",
      "referenceAnswer": "OpenSearch is a search and analytics suite",
      "category": "technology",
      "expectedScore": "0.95",
      "brand": "OpenSearch"
    }
  ]
}
```

## Running BWC Tests

### Prerequisites
1. Set the BWC version to test against:
   ```bash
   export TESTS_SEARCH_RELEVANCE_VERSION=3.0.0  # Replace with actual version
   ```

2. Build the plugin:
   ```bash
   ./gradlew build -x test
   ```

### Run All BWC Tests
```bash
./gradlew :qa:bwcTestSuite
```

### Run Only Rolling Upgrade Tests
```bash
./gradlew :qa:rolling-upgrade:testRollingUpgrade
```

### Run Individual Test Phases

**Test against OLD cluster (all nodes on old version):**
```bash
./gradlew :qa:rolling-upgrade:testAgainstOldCluster
```

**Test against MIXED cluster (1/3 upgraded):**
```bash
./gradlew :qa:rolling-upgrade:testAgainstOneThirdUpgradedCluster
```

**Test against MIXED cluster (2/3 upgraded):**
```bash
./gradlew :qa:rolling-upgrade:testAgainstTwoThirdsUpgradedCluster
```

**Test against UPGRADED cluster (all nodes upgraded):**
```bash
./gradlew :qa:rolling-upgrade:testRollingUpgrade
```

## Test Lifecycle

### Phase 1: OLD Cluster
- Creates query sets with old format (queryText + referenceAnswer only)
- Creates search configurations
- Validates resources are created correctly

### Phase 2: MIXED Cluster (First Round)
- Validates OLD format resources still work
- Creates NEW format resources (with custom fields)
- Tests both formats work simultaneously

### Phase 3: MIXED Cluster (Second Round)
- Continues validation
- Two out of three nodes now upgraded

### Phase 4: UPGRADED Cluster
- Validates all OLD format resources still work
- Validates NEW format resources work
- Tests new features (promptTemplate, ratingType, custom fields)
- Cleans up test resources

## What's Being Tested

### Query Set Format Compatibility
- ✅ Old format: `{queryText, referenceAnswer}`
- ✅ New format: `{queryText, referenceAnswer, ...customFields}`
- ✅ Parsing logic handles both formats
- ✅ Custom fields stored as `queryText#\nkey: value\nkey: value`

### LLM Judgment Format Compatibility
- ✅ Old format: No `promptTemplate`, no `llmJudgmentRatingType` (uses defaults)
- ✅ New format: Optional `promptTemplate`, optional `llmJudgmentRatingType`
- ✅ Default values applied when fields missing

### Reserved Character Validation
- ✅ Validates newline (`\n`), hash (`#`), colon (`:`) not in user input
- ✅ Ensures parsing logic won't break

## Adding New BWC Tests

To add a new BWC test:

1. **Create a test class** extending `AbstractSearchRelevanceRollingUpgradeTestCase`:
   ```java
   public class MyFeatureBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {
       public void testMyFeature_RollingUpgrade() throws Exception {
           switch (getClusterType()) {
               case OLD:
                   // Test old format
                   break;
               case MIXED:
                   // Test compatibility
                   break;
               case UPGRADED:
                   // Test new format
                   break;
           }
       }
   }
   ```

2. **Update build.gradle** if needed for new dependencies or test filters

3. **Run the test**:
   ```bash
   ./gradlew :qa:rolling-upgrade:testRollingUpgrade
   ```

## Troubleshooting

### Test Failures

**Old cluster test fails:**
- Check if the BWC version is correctly set
- Ensure the plugin artifact is available for the specified version

**Mixed cluster test fails:**
- Verify both old and new formats are handled in the code
- Check logs for parsing errors

**Upgraded cluster test fails:**
- Ensure backward compatibility is maintained
- Check if defaults are correctly applied for missing fields

### Common Issues

1. **Plugin not found**: Ensure `tests.search_relevance.version` property is set
2. **Cluster timeout**: Increase timeout in `AbstractSearchRelevanceRollingUpgradeTestCase.restClientSettings()`
3. **Version mismatch**: Check that `bwcOpenSearchVersion` matches the plugin version

## CI/CD Integration

In CI/CD pipelines, BWC tests should:
1. Run on every PR that changes data formats or APIs
2. Test against the last released version
3. Block merge if BWC tests fail

### Example CI Configuration
```yaml
- name: Run BWC Tests
  run: |
    export TESTS_SEARCH_RELEVANCE_VERSION=3.0.0
    ./gradlew :qa:bwcTestSuite
```

## References

- [OpenSearch BWC Testing Documentation](https://github.com/opensearch-project/OpenSearch/blob/main/TESTING.md#testing-backward-compatibility)
- [Neural Search BWC Tests](https://github.com/opensearch-project/neural-search/tree/main/qa/rolling-upgrade)
- [OpenSearch Upgrade Guide](https://opensearch.org/docs/latest/upgrade-to/)

## Maintenance

BWC tests should be updated whenever:
- ✅ New data formats are introduced
- ✅ API changes affect backward compatibility
- ✅ Default values change
- ✅ Parsing logic is modified

Regular review ensures that users can upgrade seamlessly without data migration.
