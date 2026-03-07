/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.common.PluginConstants;
import org.opensearch.test.OpenSearchTestCase;

public class ExperimentTests extends OpenSearchTestCase {

    public void testExperimentWithNameAndDescription() throws IOException {
        Experiment experiment = new Experiment(
            "test-id",
            "2024-01-01T00:00:00Z",
            "Test Experiment",
            "This is a test description",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            "queryset-id",
            List.of("config1", "config2"),
            List.of("judgment1"),
            10,
            new ArrayList<>()
        );

        assertEquals("test-id", experiment.id());
        assertEquals("2024-01-01T00:00:00Z", experiment.timestamp());
        assertEquals("Test Experiment", experiment.name());
        assertEquals("This is a test description", experiment.description());
        assertEquals(ExperimentType.PAIRWISE_COMPARISON, experiment.type());
        assertEquals(AsyncStatus.COMPLETED, experiment.status());
    }

    public void testExperimentWithNullNameAndDescription() throws IOException {
        Experiment experiment = new Experiment(
            "test-id",
            "2024-01-01T00:00:00Z",
            null,
            null,
            ExperimentType.POINTWISE_EVALUATION,
            AsyncStatus.PROCESSING,
            "queryset-id",
            List.of("config1"),
            List.of(),
            5,
            new ArrayList<>()
        );

        assertNull(experiment.name());
        assertNull(experiment.description());
    }

    public void testToXContentWithNameAndDescription() throws IOException {
        Experiment experiment = new Experiment(
            "test-id",
            "2024-01-01T00:00:00Z",
            "Test Experiment",
            "This is a test description",
            ExperimentType.HYBRID_OPTIMIZER,
            AsyncStatus.COMPLETED,
            "queryset-id",
            List.of("config1"),
            List.of("judgment1"),
            10,
            new ArrayList<>()
        );

        XContentBuilder builder = XContentFactory.jsonBuilder();
        experiment.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"name\":\"Test Experiment\""));
        assertTrue(json.contains("\"description\":\"This is a test description\""));
    }

    public void testToXContentWithNullNameAndDescription() throws IOException {
        Experiment experiment = new Experiment(
            "test-id",
            "2024-01-01T00:00:00Z",
            null,
            null,
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            "queryset-id",
            List.of("config1", "config2"),
            List.of("judgment1"),
            10,
            new ArrayList<>()
        );

        XContentBuilder builder = XContentFactory.jsonBuilder();
        experiment.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        // Name and description should not be present in the JSON when null
        assertFalse(json.contains("\"name\""));
        assertFalse(json.contains("\"description\""));
    }

    public void testCopyConstructorPreservesNameAndDescription() {
        Experiment original = new Experiment(
            "test-id",
            "2024-01-01T00:00:00Z",
            "Original Name",
            "Original Description",
            ExperimentType.PAIRWISE_COMPARISON,
            AsyncStatus.COMPLETED,
            "queryset-id",
            List.of("config1", "config2"),
            List.of("judgment1"),
            10,
            new ArrayList<>()
        );

        Experiment copy = new Experiment(original, true, "scheduled-job-id");

        assertEquals(original.name(), copy.name());
        assertEquals(original.description(), copy.description());
        assertTrue(copy.isScheduled());
        assertEquals("scheduled-job-id", copy.scheduledExperimentJobId());
    }

    public void testExperimentFieldConstants() {
        assertEquals("id", Experiment.ID);
        assertEquals("timestamp", Experiment.TIME_STAMP);
        assertEquals("name", PluginConstants.NAME);
        assertEquals("description", PluginConstants.DESCRIPTION);
        assertEquals("type", Experiment.TYPE);
        assertEquals("status", Experiment.STATUS);
        assertEquals("querySetId", Experiment.QUERY_SET_ID);
        assertEquals("searchConfigurationList", Experiment.SEARCH_CONFIGURATION_LIST);
        assertEquals("judgmentList", Experiment.JUDGMENT_LIST);
        assertEquals("size", Experiment.SIZE);
        assertEquals("isScheduled", Experiment.IS_SCHEDULED);
        assertEquals("scheduledExperimentJobId", Experiment.SCHEDULED_EXPERIMENT_JOB_ID);
        assertEquals("results", Experiment.RESULTS);
    }
}
