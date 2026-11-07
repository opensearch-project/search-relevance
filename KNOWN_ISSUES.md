# Known Issues

This file lists known issues in released versions of the Search Relevance plugin, which versions they affect, and how to work around them.

## Experiment APIs return 500 after upgrading: `mapper [isScheduled] cannot be changed from type [boolean] to [keyword]`

**Tracking issue:** [#600](https://github.com/opensearch-project/search-relevance/issues/600)

### Who is affected

Clusters that went through **all** of these steps:

1. The experiment index (`.plugins-search-relevance-experiment`) was created on **3.3 or earlier**.
2. The cluster was upgraded to **3.4 or 3.5**, and at least one experiment was created on that version.
3. The cluster was later upgraded to **3.6 or later**.

| Upgrade path | Affected |
|---|---|
| 3.3 → 3.4 → 3.6+ | Yes |
| 3.3 → 3.5 → 3.6+ | Yes |
| 3.3 → 3.6+ (directly) | No |
| Experiment index first created on 3.4 or later | No |

### Symptoms

- Experiment APIs, for example `GET /_plugins/_search_relevance/experiments/_search`, return HTTP 500 with:
  `mapper [isScheduled] cannot be changed from type [boolean] to [keyword]`
- The experiments page in Search Relevance Workbench fails to load.

### Cause

Versions 3.4 and 3.5 write the `isScheduled` field without first adding it to the mapping of an existing experiment index, so OpenSearch maps it as `boolean`. From 3.6, the plugin updates the index mapping to define `isScheduled` as `keyword`. OpenSearch does not allow an existing field's type to change, so the update fails every time and the experiment APIs fail with it.

### Workaround

Copy the experiments to a backup index, delete the experiment index so the plugin recreates it with the correct mapping, and copy the experiments back. Existing experiments are kept.

> [!IMPORTANT]
> - `.plugins-search-relevance-experiment` is a system index. If the Security plugin is enabled, run these requests as a super admin (for example, with the admin certificate).
> - Don't create or run experiments while you're doing this, and pause any scheduled experiments first.

1. Back up the experiments:
   ```
   POST _reindex?refresh=true
   {
     "source": { "index": ".plugins-search-relevance-experiment" },
     "dest":   { "index": "srw-experiment-backup" }
   }
   ```
2. Check that the backup holds the same number of documents as the original:
   ```
   GET .plugins-search-relevance-experiment/_count
   GET srw-experiment-backup/_count
   ```
3. Delete the experiment index:
   ```
   DELETE .plugins-search-relevance-experiment
   ```
4. Call any experiment API. The plugin then recreates the index with the current mapping:
   ```
   POST _plugins/_search_relevance/experiments/_search
   { "query": { "match_all": {} } }
   ```
5. Restore the experiments:
   ```
   POST _reindex?refresh=true
   {
     "source": { "index": "srw-experiment-backup" },
     "dest":   { "index": ".plugins-search-relevance-experiment" }
   }
   ```
6. Check that the experiments are back, then delete the backup:
   ```
   GET .plugins-search-relevance-experiment/_count
   DELETE srw-experiment-backup
   ```
