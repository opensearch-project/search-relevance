/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.algorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.opensearch.search.SearchHit;

public class TeamDraftInterleaver {

    public TeamDraftInterleaver() {}

    public Result interleave(List<SearchHit> hitsA, List<SearchHit> hitsB, int size) {
        List<SearchHit> interleaved = new ArrayList<>(size);
        Set<String> teamA = new HashSet<>();
        Set<String> teamB = new HashSet<>();
        Set<String> seen = new HashSet<>();
        int ptrA = 0;
        int ptrB = 0;

        while (interleaved.size() < size) {
            boolean aFirst = ThreadLocalRandom.current().nextBoolean();
            List<SearchHit> firstHits = aFirst ? hitsA : hitsB;
            List<SearchHit> secondHits = aFirst ? hitsB : hitsA;
            Set<String> firstTeam = aFirst ? teamA : teamB;
            Set<String> secondTeam = aFirst ? teamB : teamA;
            int firstPtr = aFirst ? ptrA : ptrB;
            int secondPtr = aFirst ? ptrB : ptrA;

            while (firstPtr < firstHits.size() && seen.contains(firstHits.get(firstPtr).getId())) {
                firstPtr++;
            }
            if (firstPtr < firstHits.size()) {
                SearchHit pick = firstHits.get(firstPtr);
                interleaved.add(pick);
                firstTeam.add(pick.getId());
                seen.add(pick.getId());
                firstPtr++;
            }
            if (interleaved.size() >= size) {
                if (aFirst) {
                    ptrA = firstPtr;
                    ptrB = secondPtr;
                } else {
                    ptrB = firstPtr;
                    ptrA = secondPtr;
                }
                break;
            }

            while (secondPtr < secondHits.size() && seen.contains(secondHits.get(secondPtr).getId())) {
                secondPtr++;
            }
            if (secondPtr < secondHits.size()) {
                SearchHit pick = secondHits.get(secondPtr);
                interleaved.add(pick);
                secondTeam.add(pick.getId());
                seen.add(pick.getId());
                secondPtr++;
            }
            if (aFirst) {
                ptrA = firstPtr;
                ptrB = secondPtr;
            } else {
                ptrB = firstPtr;
                ptrA = secondPtr;
            }
            if (ptrA >= hitsA.size() && ptrB >= hitsB.size()) {
                break;
            }
        }
        return new Result(interleaved, teamA, teamB);
    }

    public static class Result {
        private final List<SearchHit> interleavedHits;
        private final Set<String> teamA;
        private final Set<String> teamB;

        public Result(List<SearchHit> interleavedHits, Set<String> teamA, Set<String> teamB) {
            this.interleavedHits = java.util.Collections.unmodifiableList(interleavedHits);
            this.teamA = java.util.Collections.unmodifiableSet(teamA);
            this.teamB = java.util.Collections.unmodifiableSet(teamB);
        }

        public List<SearchHit> getInterleavedHits() {
            return interleavedHits;
        }

        public Set<String> getTeamA() {
            return teamA;
        }

        public Set<String> getTeamB() {
            return teamB;
        }
    }
}
