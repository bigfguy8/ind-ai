package com.nova.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class MemoryStore {

    public interface BlockCallback {
        void onBlock(String block);
    }

    private static final String PREFS = "nova_memory";
    private static final String KEY = "facts";

    private final SharedPreferences prefs;
    private final VectorStore vectors;
    private final EmbeddingClient embedder;

    public MemoryStore(Context c) {
        this(c, null);
    }

    public MemoryStore(Context c, ApiConfig config) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        vectors = new VectorStore(c);
        embedder = config != null ? new EmbeddingClient(config) : null;
        migrateIfNeeded();
    }

    private void migrateIfNeeded() {
        if (vectors.count() == 0) {
            List<String> existing = load();
            for (String fact : existing) {
                embedAndStore(fact);
            }
        }
    }

    public List<String> load() {
        List<String> facts = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                facts.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
        return facts;
    }

    public void save(List<String> facts) {
        try {
            prefs.edit().putString(KEY, new JSONArray(facts).toString()).apply();
        } catch (Exception ignored) {}
    }

    public void add(String fact) {
        fact = fact == null ? "" : fact.trim();
        if (fact.isEmpty()) return;
        List<String> facts = load();
        for (String existing : facts) {
            if (existing.equalsIgnoreCase(fact)) return;
        }
        facts.add(fact);
        save(facts);
        embedAndStore(fact);
    }

    public void remove(String fact) {
        if (fact == null) return;
        List<String> facts = load();
        facts.remove(fact);
        save(facts);
        vectors.removeByText(fact);
    }

    private void embedAndStore(final String fact) {
        if (embedder == null) {
            vectors.add(fact, EmbeddingClient.fallback(fact));
            return;
        }
        embedder.embed(fact, new EmbeddingClient.Callback() {
            @Override public void onVector(float[] v) {
                vectors.add(fact, v);
            }
            @Override public void onFailure(String reason) {
                vectors.add(fact, EmbeddingClient.fallback(fact));
            }
        });
    }

    public String asSystemBlock() {
        List<String> facts = load();
        if (facts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nPERSISTENT MEMORY\n");
        sb.append("Facts the user has told you to remember permanently, ");
        sb.append("across all sessions. Treat these as always true unless the user updates them:\n");
        for (String f : facts) sb.append("- ").append(f).append("\n");
        return sb.toString();
    }

    public void relevantBlockAsync(final String query, final int k,
                                   final BlockCallback cb) {
        if (embedder == null || vectors.count() == 0 || embedder.isUnavailable()) {
            cb.onBlock(asSystemBlock());
            return;
        }
        embedder.embed(query, new EmbeddingClient.Callback() {
            @Override public void onVector(float[] qv) {
                List<VectorStore.Entry> hits = vectors.search(qv, k);
                if (hits.isEmpty()) { cb.onBlock(asSystemBlock()); return; }
                StringBuilder sb = new StringBuilder();
                sb.append("\n\nRELEVANT MEMORY\n");
                sb.append("Facts the user has told you to remember that may be ");
                sb.append("relevant to this turn:\n");
                int kept = 0;
                for (VectorStore.Entry e : hits) {
                    if (e.score < 0.15) continue;
                    sb.append("- ").append(e.text).append("\n");
                    kept++;
                }
                if (kept == 0) { cb.onBlock(asSystemBlock()); return; }
                cb.onBlock(sb.toString());
            }
            @Override public void onFailure(String reason) {
                cb.onBlock(asSystemBlock());
            }
        });
    }

    public boolean isEmpty() {
        return load().isEmpty();
    }

    public int vectorCount() {
        return vectors.count();
    }
}
