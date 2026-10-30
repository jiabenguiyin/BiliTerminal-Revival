package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.json.JSONObject;
import org.junit.Test;

public class InteractionProgressStoreTest {
    @Test
    public void snapshotRoundTripKeepsPlaybackBranchAndVariables() throws Exception {
        InteractionProgressStore.Snapshot original = new InteractionProgressStore.Snapshot();
        original.graphVersion = 123L;
        original.edgeId = 456L;
        original.cid = 789L;
        original.positionMs = 12_345L;
        original.variables.put("score", 17L);
        original.variables.put("ending", 2L);

        InteractionProgressStore.Snapshot restored = InteractionProgressStore.fromJson(
                new JSONObject(InteractionProgressStore.toJson(original).toString()));

        assertNotNull(restored);
        assertEquals(original.graphVersion, restored.graphVersion);
        assertEquals(original.edgeId, restored.edgeId);
        assertEquals(original.cid, restored.cid);
        assertEquals(original.positionMs, restored.positionMs);
        assertEquals(original.variables, restored.variables);
    }
}
