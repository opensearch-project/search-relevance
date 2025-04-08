/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.util.Optional;

import org.opensearch.search.sort.SortOrder;

public class SearchParams {
    private static final class Defaults {
        static final int SIZE = 1000;
        static final String SORT_FIELD = "timestamp";
        static final SortOrder SORT_ORDER = SortOrder.DESC;
    }

    private final int size;
    private final String sortField;
    private final SortOrder sortOrder;

    private SearchParams(Builder builder) {
        this.size = builder.size;
        this.sortField = builder.sortField;
        this.sortOrder = builder.sortOrder;
    }

    // Getters
    public int getSize() {
        return size;
    }

    public String getSortField() {
        return sortField;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int size = Defaults.SIZE;
        private String sortField = Defaults.SORT_FIELD;
        private SortOrder sortOrder = Defaults.SORT_ORDER;

        public Builder size(Integer value) {
            this.size = Optional.ofNullable(value).filter(s -> s > 0).orElse(Defaults.SIZE);
            return this;
        }

        public Builder sortField(String field) {
            this.sortField = Optional.ofNullable(field).orElse(Defaults.SORT_FIELD);
            return this;
        }

        public Builder sortOrder(String order) {
            this.sortOrder = Optional.ofNullable(order).filter("asc"::equals).map(s -> SortOrder.ASC).orElse(Defaults.SORT_ORDER);
            return this;
        }

        public SearchParams build() {
            return new SearchParams(this);
        }
    }
}
