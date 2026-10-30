package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.InteractionVideoData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Persists the local resume point for an interactive video. */
public final class InteractionProgressStore {
    private static final String PREF_KEY = "player_interaction_progress_v1";

    private InteractionProgressStore() {
    }

    public static final class Snapshot {
        public long graphVersion;
        public long edgeId;
        public long cid;
        public long positionMs;
        public final Map<String, Long> variables = new HashMap<>();
    }

    public static synchronized Snapshot load(long aid) {
        if (aid <= 0) return null;
        try {
            JSONObject root = new JSONObject(SharedPreferencesUtil.getString(PREF_KEY, "{}"));
            return fromJson(root.optJSONObject(String.valueOf(aid)));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static synchronized void save(long aid, long graphVersion, long edgeId,
                                         long cid, long positionMs,
                                         List<InteractionVideoData.InteractionHiddenVar> variables) {
        if (aid <= 0 || graphVersion <= 0) return;
        try {
            JSONObject root = new JSONObject(SharedPreferencesUtil.getString(PREF_KEY, "{}"));
            Snapshot snapshot = new Snapshot();
            snapshot.graphVersion = graphVersion;
            snapshot.edgeId = Math.max(0L, edgeId);
            snapshot.cid = Math.max(0L, cid);
            snapshot.positionMs = Math.max(0L, positionMs);
            if (variables != null) {
                for (InteractionVideoData.InteractionHiddenVar variable : variables) {
                    if (variable == null) continue;
                    String key = !isEmpty(variable.idV2) ? variable.idV2 : variable.id;
                    if (!isEmpty(key)) snapshot.variables.put(key, variable.value);
                }
            }
            root.put(String.valueOf(aid), toJson(snapshot));
            SharedPreferencesUtil.putString(PREF_KEY, root.toString());
        } catch (Exception ignored) {
        }
    }

    public static JSONObject toJson(Snapshot snapshot) throws JSONException {
        JSONObject json = new JSONObject();
        if (snapshot == null) return json;
        json.put("graph_version", snapshot.graphVersion);
        json.put("edge_id", snapshot.edgeId);
        json.put("cid", snapshot.cid);
        json.put("position_ms", snapshot.positionMs);
        JSONObject variables = new JSONObject();
        for (Map.Entry<String, Long> entry : snapshot.variables.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                variables.put(entry.getKey(), entry.getValue());
            }
        }
        json.put("variables", variables);
        return json;
    }

    public static Snapshot fromJson(JSONObject json) {
        if (json == null) return null;
        Snapshot snapshot = new Snapshot();
        snapshot.graphVersion = json.optLong("graph_version", 0L);
        snapshot.edgeId = json.optLong("edge_id", 0L);
        snapshot.cid = json.optLong("cid", 0L);
        snapshot.positionMs = Math.max(0L, json.optLong("position_ms", 0L));
        JSONObject variables = json.optJSONObject("variables");
        if (variables != null) {
            Iterator<String> keys = variables.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    snapshot.variables.put(key, variables.getLong(key));
                } catch (Exception ignored) {
                }
            }
        }
        return snapshot;
    }

    public static void apply(Snapshot snapshot, InteractionVideoData data) {
        if (snapshot == null || data == null || data.hiddenVars == null) return;
        for (InteractionVideoData.InteractionHiddenVar variable : data.hiddenVars) {
            if (variable == null) continue;
            Long value = snapshot.variables.get(variable.idV2);
            if (value == null) value = snapshot.variables.get(variable.id);
            if (value != null) variable.value = value;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
