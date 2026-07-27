/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import org.opensearch.Version;
import org.opensearch.searchrelevance.utils.ClusterUtil;

/**
 * Holds the minimum cluster node versions required by request parameters, so all version gates for
 * backward-incompatible changes live in a single place (modeled on neural-search's
 * {@code MinClusterVersionUtil}). Each gate reads the cluster's minimum node version via the
 * {@link ClusterUtil} singleton, so a feature only activates once every node can understand it.
 */
public final class MinClusterVersionUtil {

    private MinClusterVersionUtil() {}

    /**
     * The first version that serializes {@code existingJudgments} (a string list) at the trailing
     * position of PutLlmJudgmentRequest. Older versions wrote an optional boolean there (the removed
     * "overwriteCache"), so the wire format is gated on the cluster's minimum node version being at
     * least this.
     *
     * <p>NOTE: set to {@code V_3_8_0} because this ships in the current 3.8 line. This MUST be bumped
     * to {@code V_3_9_0} before the 3.9 release (tracked in a GitHub issue), otherwise a 3.8 node in a
     * mixed 3.8/3.9 cluster would be sent the new list format it cannot deserialize.
     */
    private static final Version MINIMAL_SUPPORTED_VERSION_EXISTING_JUDGMENTS = Version.V_3_8_0;

    /**
     * @return true if every node in the cluster is on or after the minimum version that supports the
     *         {@code existingJudgments} field on PutLlmJudgmentRequest
     */
    public static boolean isClusterOnOrAfterMinReqVersionForExistingJudgments() {
        return ClusterUtil.instance().getClusterMinVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_EXISTING_JUDGMENTS);
    }
}
