/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opensearch.search.SearchHit;
import org.opensearch.test.OpenSearchTestCase;

public class TeamDraftInterleaverTests extends OpenSearchTestCase {

    private final TeamDraftInterleaver interleaver = new TeamDraftInterleaver();

    private List<SearchHit> createHits(String... ids) {
        List<SearchHit> hits = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            hits.add(new SearchHit(i + 1, ids[i], Collections.emptyMap(), Collections.emptyMap()));
        }
        return hits;
    }

    public void testBothListsContributed() { // happy path
        List<SearchHit> hitsA = createHits("a1", "a2", "a3");
        List<SearchHit> hitsB = createHits("b1", "b2", "b3");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 6);

        assertFalse(result.getTeamA().isEmpty());
        assertFalse(result.getTeamB().isEmpty());
        assertEquals(6, result.getInterleavedHits().size());
    }

    public void testNoDuplicatesInResult() {
        List<SearchHit> hitsA = createHits("a1", "a2", "a3");
        List<SearchHit> hitsB = createHits("b1", "b2", "b3");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 6);

        Set<String> seen = new java.util.HashSet<>();  // seen is a set that checks if the item already exists. If that happens --> test
                                                       // fails
        for (SearchHit hit : result.getInterleavedHits()) {
            assertTrue("Duplicate found: " + hit.getId(), seen.add(hit.getId()));
        }
    }

    public void testOverlappingDocsDeduplicated() {
        List<SearchHit> hitsA = createHits("doc1", "doc2", "doc3");
        List<SearchHit> hitsB = createHits("doc1", "doc4", "doc5");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 5);

        // Once the interleaving is performed, count how many times "doc1" appears
        long doc1Count = result.getInterleavedHits().stream().filter(h -> h.getId().equals("doc1")).count();
                                                                                                               // in the result. must be
                                                                                                               // exactly 1 and not greater.
        assertEquals(1, doc1Count);
    }

    public void testOverlappingDocAssignedToOneTeam() {
        List<SearchHit> hitsA = createHits("shared", "a1", "a2");
        List<SearchHit> hitsB = createHits("shared", "b1", "b2");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 5);

        boolean inA = result.getTeamA().contains("shared");
        boolean inB = result.getTeamB().contains("shared");
        assertTrue("shared must be in exactly one team", inA ^ inB);            // XOR operation is used which will check exactly one must
                                                                                // be true. not both, not neither.
    }

    public void testSizeLimitRespected() {
        List<SearchHit> hitsA = createHits("a1", "a2", "a3", "a4", "a5");
        List<SearchHit> hitsB = createHits("b1", "b2", "b3", "b4", "b5");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 4);

        assertEquals(4, result.getInterleavedHits().size());
    }

    public void testEmptyListA() {
        List<SearchHit> hitsA = createHits();
        List<SearchHit> hitsB = createHits("b1", "b2", "b3");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 3);

        assertEquals(3, result.getInterleavedHits().size());
        assertTrue(result.getTeamA().isEmpty());
        assertEquals(3, result.getTeamB().size());
    }

    public void testEmptyListB() {
        List<SearchHit> hitsA = createHits("a1", "a2", "a3");
        List<SearchHit> hitsB = createHits();

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 3);

        assertEquals(3, result.getInterleavedHits().size());
        assertEquals(3, result.getTeamA().size());
        assertTrue(result.getTeamB().isEmpty());
    }

    public void testBothListsEmpty() {
        List<SearchHit> hitsA = createHits();
        List<SearchHit> hitsB = createHits();

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 5);

        assertEquals(0, result.getInterleavedHits().size());
    }

    public void testTeamAssignmentCoversAllHits() {
        List<SearchHit> hitsA = createHits("a1", "a2", "a3");
        List<SearchHit> hitsB = createHits("b1", "b2", "b3");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 6);

        for (SearchHit hit : result.getInterleavedHits()) {
            boolean inA = result.getTeamA().contains(hit.getId());
            boolean inB = result.getTeamB().contains(hit.getId());
            assertTrue("Hit " + hit.getId() + " must belong to a team", inA || inB);
            assertFalse("Hit " + hit.getId() + " can't be in both teams", inA && inB);
        }
    }

    public void testUnequalListSizes() {
        List<SearchHit> hitsA = createHits("a1", "a2");
        List<SearchHit> hitsB = createHits("b1", "b2", "b3", "b4", "b5");

        TeamDraftInterleaver.Result result = interleaver.interleave(hitsA, hitsB, 7);

        assertEquals(7, result.getInterleavedHits().size());
    }
}
