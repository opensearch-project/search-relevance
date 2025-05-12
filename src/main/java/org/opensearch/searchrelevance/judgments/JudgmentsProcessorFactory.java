/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import org.opensearch.common.inject.Inject;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.transport.client.Client;

public class JudgmentsProcessorFactory {
    private final MLAccessor mlAccessor;
    private final JudgmentDao judgmentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;

    private final Client client;

    @Inject
    public JudgmentsProcessorFactory(
        MLAccessor mlAccessor,
        JudgmentDao judgmentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        Client client
    ) {
        this.judgmentDao = judgmentDao;
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.client = client;
    }

    public BaseJudgmentsProcessor getProcessor(JudgmentType type) {
        return switch (type) {
            case LLM_JUDGMENT -> new LlmJudgmentsProcessor(mlAccessor, querySetDao, searchConfigurationDao, client);
            case UBI_JUDGMENT -> new UbiJudgmentsProcessor(client);
            default -> throw new IllegalArgumentException("Unsupported experiment type: " + type);
        };
    }
}
