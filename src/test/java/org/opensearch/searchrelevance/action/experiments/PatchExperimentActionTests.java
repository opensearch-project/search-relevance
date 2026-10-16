/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.experiments;

import java.io.IOException;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.transport.experiment.PatchExperimentRequest;
import org.opensearch.test.OpenSearchTestCase;

public class PatchExperimentActionTests extends OpenSearchTestCase {

    public void testStreamsWithNameAndDescription() throws IOException {
        PatchExperimentRequest request = new PatchExperimentRequest("experiment-123", "Test Name", "Test Description");

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PatchExperimentRequest serialized = new PatchExperimentRequest(in);

        assertEquals("experiment-123", serialized.getExperimentId());
        assertEquals("Test Name", serialized.getName());
        assertEquals("Test Description", serialized.getDescription());
    }

    public void testStreamsWithNameOnly() throws IOException {
        PatchExperimentRequest request = new PatchExperimentRequest("experiment-123", "Test Name", null);

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PatchExperimentRequest serialized = new PatchExperimentRequest(in);

        assertEquals("experiment-123", serialized.getExperimentId());
        assertEquals("Test Name", serialized.getName());
        assertNull(serialized.getDescription());
    }

    public void testStreamsWithDescriptionOnly() throws IOException {
        PatchExperimentRequest request = new PatchExperimentRequest("experiment-123", null, "Test Description");

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        StreamInput in = StreamInput.wrap(output.bytes().toBytesRef().bytes);
        PatchExperimentRequest serialized = new PatchExperimentRequest(in);

        assertEquals("experiment-123", serialized.getExperimentId());
        assertNull(serialized.getName());
        assertEquals("Test Description", serialized.getDescription());
    }

    public void testValidationPassesWithName() {
        PatchExperimentRequest request = new PatchExperimentRequest("experiment-123", "Test Name", null);
        assertNull(request.validate());
    }

    public void testValidationPassesWithDescription() {
        PatchExperimentRequest request = new PatchExperimentRequest("experiment-123", null, "Test Description");
        assertNull(request.validate());
    }

    public void testValidationPassesWithBoth() {
        PatchExperimentRequest request = new PatchExperimentRequest("experiment-123", "Test Name", "Test Description");
        assertNull(request.validate());
    }

    public void testValidationFailsWithoutExperimentId() {
        PatchExperimentRequest request = new PatchExperimentRequest(null, "Test Name", "Test Description");
        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Experiment ID cannot be null or empty"));
    }

    public void testValidationFailsWithEmptyExperimentId() {
        PatchExperimentRequest request = new PatchExperimentRequest("  ", "Test Name", "Test Description");
        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Experiment ID cannot be null or empty"));
    }

    public void testValidationFailsWithoutNameOrDescription() {
        PatchExperimentRequest request = new PatchExperimentRequest("experiment-123", null, null);
        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("At least one of 'name' or 'description' must be provided"));
    }
}
