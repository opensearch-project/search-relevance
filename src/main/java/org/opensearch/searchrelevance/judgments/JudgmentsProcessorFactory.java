/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class JudgmentsProcessorFactory {
    private final MLAccessor mlAccessor;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentDao judgmentDao;
    private final Client client;
    private final ClusterService clusterService;
    private final ThreadPool threadPool;

    @Inject
    public JudgmentsProcessorFactory(
        MLAccessor mlAccessor,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentDao judgmentDao,
        Client client,
        ClusterService clusterService,
        ThreadPool threadPool
    ) {
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentDao = judgmentDao;
        this.client = client;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
    }

    public BaseJudgmentsProcessor getProcessor(JudgmentType type) {
        return switch (type) {
            case LLM_JUDGMENT -> new LlmJudgmentsProcessor(
                mlAccessor,
                querySetDao,
                searchConfigurationDao,
                judgmentDao,
                client,
                clusterService,
                threadPool
            );
            case UBI_JUDGMENT -> new UbiJudgmentsProcessor(client);
            case IMPORT_JUDGMENT -> new ImportJudgmentsProcessor(client);
            default -> throw new IllegalArgumentException("Unsupported judgment type: " + type);
        };
    }
}
