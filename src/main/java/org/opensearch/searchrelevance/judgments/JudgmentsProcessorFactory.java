/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import org.opensearch.common.inject.Inject;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.transport.client.Client;

public class JudgmentsProcessorFactory {
    private final MLAccessor mlAccessor;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentCacheDao judgmentCacheDao;

    private final Client client;

    @Inject
    public JudgmentsProcessorFactory(
        MLAccessor mlAccessor,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentCacheDao judgmentCacheDao,
        Client client
    ) {
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentCacheDao = judgmentCacheDao;
        this.client = client;
    }

    public BaseJudgmentsProcessor getProcessor(JudgmentType type) {
        return switch (type) {
            case LLM_JUDGMENT -> new LlmJudgmentsProcessor(mlAccessor, querySetDao, searchConfigurationDao, judgmentCacheDao, client);
            case UBI_JUDGMENT -> new UbiJudgmentsProcessor(client);
            default -> throw new IllegalArgumentException("Unsupported experiment type: " + type);
        };
    }
}
