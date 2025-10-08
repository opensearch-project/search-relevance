/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.Client;

public class ImportJudgmentsProcessorTests extends OpenSearchTestCase {

    private ImportJudgmentsProcessor processor;
    private Client mockClient;

    @Mock
    private SearchRelevanceSettingsAccessor mockSettingsAccessor;

    private EventStatsManager eventStatsManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        mockClient = mock(Client.class);

        // Configure the mock settings accessor
        when(mockSettingsAccessor.isStatsEnabled()).thenReturn(false);

        // Initialize and configure EventStatsManager with our mock
        eventStatsManager = EventStatsManager.instance();
        eventStatsManager.initialize(mockSettingsAccessor);

        processor = new ImportJudgmentsProcessor(mockClient);
    }

    public void testGetJudgmentType() {
        assertEquals(JudgmentType.IMPORT_JUDGMENT, processor.getJudgmentType());
    }

    public void testGenerateJudgmentRatingSuccess() throws Exception {
        // Prepare metadata
        Map<String, Object> metadata = new HashMap<>();
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();

        // Create a sample query judgment with ratings
        Map<String, Object> queryJudgment1 = new HashMap<>();
        queryJudgment1.put("query", "test query");

        List<Map<String, Object>> ratings = new ArrayList<>();
        ratings.add(Map.of("docId", "doc1", "rating", "1.0"));
        ratings.add(Map.of("docId", "doc2", "rating", "0.5"));
        ratings.add(Map.of("docId", "doc3", "rating", 0.75)); // numeric rating
        queryJudgment1.put("ratings", ratings);

        judgmentRatings.add(queryJudgment1);
        metadata.put("judgmentRatings", judgmentRatings);

        // Execute the method and capture the result
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Map<String, Object>>> resultRef = new AtomicReference<>();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        processor.generateJudgmentRating(metadata, new ActionListener<>() {
            @Override
            public void onResponse(List<Map<String, Object>> response) {
                resultRef.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull("No exception should be thrown", exceptionRef.get());
        assertNotNull("Result should not be null", resultRef.get());

        List<Map<String, Object>> result = resultRef.get();
        assertEquals("Should have one query result", 1, result.size());

        Map<String, Object> queryResult = result.get(0);
        assertEquals("Query text should match", "test query", queryResult.get("query"));

        List<Map<String, String>> ratingsResult = (List<Map<String, String>>) queryResult.get("ratings");
        assertEquals("Should have three rating entries", 3, ratingsResult.size());

        // Check first rating
        Map<String, String> firstRating = ratingsResult.get(0);
        assertEquals("doc1", firstRating.get("docId"));
        assertEquals("1.0", firstRating.get("rating"));

        // Check second rating
        Map<String, String> secondRating = ratingsResult.get(1);
        assertEquals("doc2", secondRating.get("docId"));
        assertEquals("0.5", secondRating.get("rating"));

        // Check third rating that was numeric in origin
        Map<String, String> thirdRating = ratingsResult.get(2);
        assertEquals("doc3", thirdRating.get("docId"));
        assertEquals("0.75", thirdRating.get("rating"));
    }

    public void testGenerateJudgmentRatingWithEmptyDocId() throws Exception {
        // Prepare metadata with empty docId
        Map<String, Object> metadata = new HashMap<>();
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();

        Map<String, Object> queryJudgment = new HashMap<>();
        queryJudgment.put("query", "test query");

        List<Map<String, Object>> ratings = new ArrayList<>();
        ratings.add(Map.of("docId", "", "rating", "1.0")); // Empty docId
        queryJudgment.put("ratings", ratings);

        judgmentRatings.add(queryJudgment);
        metadata.put("judgmentRatings", judgmentRatings);

        // Execute and verify exception
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        processor.generateJudgmentRating(metadata, new ActionListener<>() {
            @Override
            public void onResponse(List<Map<String, Object>> response) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Exception should be thrown", exceptionRef.get());
        assertTrue("Should be SearchRelevanceException", exceptionRef.get() instanceof SearchRelevanceException);
        assertTrue(
            "Exception message should mention empty docId",
            exceptionRef.get().getMessage().contains("docId for queryText test query must not be null or empty")
        );
    }

    public void testGenerateJudgmentRatingWithNullRating() throws Exception {
        // Prepare metadata with null rating
        Map<String, Object> metadata = new HashMap<>();
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();

        Map<String, Object> queryJudgment = new HashMap<>();
        queryJudgment.put("query", "test query");

        List<Map<String, Object>> ratings = new ArrayList<>();
        Map<String, Object> ratingWithNullValue = new HashMap<>();
        ratingWithNullValue.put("docId", "doc1");
        ratingWithNullValue.put("rating", null); // Null rating
        ratings.add(ratingWithNullValue);
        queryJudgment.put("ratings", ratings);

        judgmentRatings.add(queryJudgment);
        metadata.put("judgmentRatings", judgmentRatings);

        // Execute and verify exception
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        processor.generateJudgmentRating(metadata, new ActionListener<>() {
            @Override
            public void onResponse(List<Map<String, Object>> response) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Exception should be thrown", exceptionRef.get());
        assertTrue("Should be SearchRelevanceException", exceptionRef.get() instanceof SearchRelevanceException);
        assertTrue(
            "Exception message should mention null rating",
            exceptionRef.get().getMessage().contains("rating for queryText test query must not be null")
        );
    }

    public void testGenerateJudgmentRatingWithInvalidRating() throws Exception {
        // Prepare metadata with invalid rating (not a number)
        Map<String, Object> metadata = new HashMap<>();
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();

        Map<String, Object> queryJudgment = new HashMap<>();
        queryJudgment.put("query", "test query");

        List<Map<String, Object>> ratings = new ArrayList<>();
        ratings.add(Map.of("docId", "doc1", "rating", "not-a-number")); // Invalid rating
        queryJudgment.put("ratings", ratings);

        judgmentRatings.add(queryJudgment);
        metadata.put("judgmentRatings", judgmentRatings);

        // Execute and verify exception
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        processor.generateJudgmentRating(metadata, new ActionListener<>() {
            @Override
            public void onResponse(List<Map<String, Object>> response) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Exception should be thrown", exceptionRef.get());
        assertTrue("Should be SearchRelevanceException", exceptionRef.get() instanceof SearchRelevanceException);
        assertTrue(
            "Exception message should mention invalid rating",
            exceptionRef.get().getMessage().contains("rating 'not-a-number' for queryText test query must be a valid float")
        );
    }

    public void testGenerateJudgmentRatingWithInvalidRatingsType() throws Exception {
        // Prepare metadata with ratings that is not a list
        Map<String, Object> metadata = new HashMap<>();
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();

        Map<String, Object> queryJudgment = new HashMap<>();
        queryJudgment.put("query", "test query");
        queryJudgment.put("ratings", "not a list"); // Invalid ratings type

        judgmentRatings.add(queryJudgment);
        metadata.put("judgmentRatings", judgmentRatings);

        // Execute and verify exception
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        processor.generateJudgmentRating(metadata, new ActionListener<>() {
            @Override
            public void onResponse(List<Map<String, Object>> response) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                exceptionRef.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Exception should be thrown", exceptionRef.get());
        assertTrue("Should be SearchRelevanceException", exceptionRef.get() instanceof SearchRelevanceException);
        assertTrue(
            "Exception message should mention invalid ratings type",
            exceptionRef.get().getMessage().contains("queryText test query must have a list of rating data")
        );
    }
}
