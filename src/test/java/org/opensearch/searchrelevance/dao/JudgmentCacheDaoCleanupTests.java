/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.test.OpenSearchTestCase;

import lombok.SneakyThrows;

/**
 * Tests for JudgmentCacheDao cleanup logic with the TTL setting.
 */
public class JudgmentCacheDaoCleanupTests extends OpenSearchTestCase {

    private SearchRelevanceIndicesManager mockIndicesManager;
    private SearchRelevanceSettingsAccessor mockSettingsAccessor;
    private JudgmentCacheDao dao;

    @Override
    @SneakyThrows
    public void setUp() {
        super.setUp();
        mockIndicesManager = mock(SearchRelevanceIndicesManager.class);
        mockSettingsAccessor = mock(SearchRelevanceSettingsAccessor.class);
        dao = new JudgmentCacheDao(mockIndicesManager);
    }

    public void testCleanup_NoSettingsAccessor_IsNoOp() {
        // Don't call setSettingsAccessor
        dao.cleanupStaleEntries();
        verify(mockIndicesManager, never()).deleteByQuery(any(), any(), any());
    }

    public void testCleanup_TtlDisabled_IsNoOp() {
        dao.setSettingsAccessor(mockSettingsAccessor);
        when(mockSettingsAccessor.getJudgmentCacheTtl()).thenReturn(TimeValue.MINUS_ONE);

        dao.cleanupStaleEntries();
        verify(mockIndicesManager, never()).deleteByQuery(any(), any(), any());
    }

    public void testCleanup_TtlEnabled_TriggersDeleteByQuery() {
        dao.setSettingsAccessor(mockSettingsAccessor);
        when(mockSettingsAccessor.getJudgmentCacheTtl()).thenReturn(TimeValue.timeValueDays(90));

        dao.cleanupStaleEntries();
        verify(mockIndicesManager).deleteByQuery(any(QueryBuilder.class), eq(SearchRelevanceIndices.JUDGMENT_CACHE), any());
    }

    public void testCleanup_VeryShortTtl_UsesMinimum1Day() {
        dao.setSettingsAccessor(mockSettingsAccessor);
        when(mockSettingsAccessor.getJudgmentCacheTtl()).thenReturn(TimeValue.timeValueHours(1));

        dao.cleanupStaleEntries();
        // Should still trigger cleanup (with 1 day minimum)
        verify(mockIndicesManager).deleteByQuery(any(QueryBuilder.class), eq(SearchRelevanceIndices.JUDGMENT_CACHE), any());
    }

    public void testCleanupExplicitDays_AlwaysTriggersDeleteByQuery() {
        // No settings accessor set
        dao.cleanupStaleEntries(30);
        verify(mockIndicesManager).deleteByQuery(any(QueryBuilder.class), eq(SearchRelevanceIndices.JUDGMENT_CACHE), any());
    }
}
