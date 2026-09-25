package com.indai.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MemoryStore {

    public interface BlockCallback {
        void onBlock(String block);
    }

    private static final String PREFS = "nova_memory";
    private static final String KEY = "facts_v2";

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
        // Migrate old flat-string format (key "facts") to new object format
        if (!prefs.contains(KEY) && prefs.contains("facts")) {
            try {
                JSONArray old = new JSONArray(prefs.getString("facts", "[]"));
                List<MemoryEntry> entries = new ArrayList<>();
                for (int i = 0; i < old.length(); i++) {
                    String t = old.optString(i, "").trim();
                    if (!t.isEmpty()) {
                        entries.add(new MemoryEntry(t, MemoryEntry.CAT_MISC,
                                System.currentTimeMillis()));
                    }
                }
                save(entries);
                prefs.edit().remove("facts").apply();
            } catch (Exception ignored) {}
        }

        // Sync new entries into vector store
        if (vectors.count() == 0) {
            for (MemoryEntry e : load()) embedAndStore(e);
        }
    }

    // ------------------------------------------------------------------
    //  CRUD
    // ------------------------------------------------------------------

    public List<MemoryEntry> load() {
        List<MemoryEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new MemoryEntry(
                        o.optString("text", ""),
                        o.optString("category", MemoryEntry.CAT_MISC),
                        o.optLong("created_at", System.currentTimeMillis())));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public List<String> loadFlat() {
        List<String> out = new ArrayList<>();
        for (MemoryEntry e : load()) out.add(e.text);
        return out;
    }

    public void save(List<MemoryEntry> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (MemoryEntry e : entries) {
                JSONObject o = new JSONObject();
                o.put("text", e.text);
                o.put("category", e.category);
                o.put("created_at", e.createdAt);
                arr.put(o);
            }
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void add(String fact) {
        add(fact, MemoryEntry.CAT_MISC);
    }

    public void add(String fact, String category) {
        fact = fact == null ? "" : fact.trim();
        if (fact.isEmpty()) return;
        List<MemoryEntry> entries = load();
        for (MemoryEntry e : entries) {
            if (e.text.equalsIgnoreCase(fact)) {
                // Update category if it was Misc and we now know better
                if (MemoryEntry.CAT_MISC.equals(e.category)
                        && !MemoryEntry.CAT_MISC.equals(category)) {
                    e.category = MemoryEntry.normalizeCategory(category);
                    save(entries);
                }
                return;
            }
        }
        MemoryEntry entry = new MemoryEntry(fact,
                MemoryEntry.normalizeCategory(category),
                System.currentTimeMillis());
        entries.add(entry);
        save(entries);
        embedAndStore(entry);
    }

    public void remove(String fact) {
        if (fact == null) return;
        List<MemoryEntry> entries = load();
        List<MemoryEntry> kept = new ArrayList<>();
        for (MemoryEntry e : entries) {
            if (!e.text.equals(fact)) kept.add(e);
        }
        save(kept);
        vectors.removeByText(fact);
    }

    public void update(String oldText, String newText, String newCategory) {
        if (oldText == null || newText == null) return;
        List<MemoryEntry> entries = load();
        for (MemoryEntry e : entries) {
            if (e.text.equals(oldText)) {
                e.text = newText.trim();
                e.category = MemoryEntry.normalizeCategory(newCategory);
                break;
            }
        }
        save(entries);
        vectors.removeByText(oldText);
        embedAndStore(new MemoryEntry(newText.trim(),
                newCategory, System.currentTimeMillis()));
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
        vectors.clear();
    }

    // ------------------------------------------------------------------
    //  Search & filter
    // ------------------------------------------------------------------

    public List<MemoryEntry> filter(String query, String category) {
        List<MemoryEntry> all = load();
        List<MemoryEntry> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (MemoryEntry e : all) {
            if (category != null && !category.isEmpty()
                    && !category.equals(e.category)) continue;
            if (!q.isEmpty() && !e.text.toLowerCase().contains(q)) continue;
            out.add(e);
        }
        return out;
    }

    public int countInCategory(String category) {
        int n = 0;
        for (MemoryEntry e : load()) {
            if (e.category.equals(category)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    //  Export / Import
    // ------------------------------------------------------------------

    public String exportJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("exported_at", System.currentTimeMillis());
            JSONArray arr = new JSONArray();
            for (MemoryEntry e : load()) {
                JSONObject o = new JSONObject();
                o.put("text", e.text);
                o.put("category", e.category);
                o.put("created_at", e.createdAt);
                arr.put(o);
            }
            root.put("memories", arr);
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public int importJson(String raw) {
        if (raw == null) return 0;
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray("memories");
            if (arr == null) {
                // Maybe raw array
                arr = new JSONArray(raw);
            }
            int added = 0;
            List<MemoryEntry> existing = load();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String text = o.optString("text", "").trim();
                if (text.isEmpty()) continue;
                boolean dup = false;
                for (MemoryEntry e : existing) {
                    if (e.text.equalsIgnoreCase(text)) { dup = true; break; }
                }
                if (dup) continue;
                String cat = o.optString("category", MemoryEntry.CAT_MISC);
                long ts = o.optLong("created_at", System.currentTimeMillis());
                existing.add(new MemoryEntry(text, cat, ts));
                added++;
            }
            save(existing);
            // Re-embed everything new
            for (int i = existing.size() - added; i < existing.size(); i++) {
                embedAndStore(existing.get(i));
            }
            return added;
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    //  Embedding
    // ------------------------------------------------------------------

    private void embedAndStore(final MemoryEntry entry) {
        if (embedder == null) {
            vectors.add(entry.text, EmbeddingClient.fallback(entry.text));
            return;
        }
        embedder.embed(entry.text, new EmbeddingClient.Callback() {
            @Override public void onVector(float[] v) {
                vectors.add(entry.text, v);
            }
            @Override public void onFailure(String reason) {
                vectors.add(entry.text, EmbeddingClient.fallback(entry.text));
            }
        });
    }

    // ------------------------------------------------------------------
    //  Prompt injection
    // ------------------------------------------------------------------

    public String asSystemBlock() {
        List<MemoryEntry> entries = load();
        if (entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nPERSISTENT MEMORY\n");
        sb.append("Facts the user has told you to remember permanently, ");
        sb.append("across all sessions. Treat these as always true unless the user updates them:\n");
        for (MemoryEntry e : entries) {
            sb.append("- [").append(e.category).append("] ").append(e.text).append("\n");
        }
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
                // Build map: text -> category
                java.util.Map<String, String> catMap = new java.util.HashMap<>();
                for (MemoryEntry e : load()) catMap.put(e.text, e.category);

                StringBuilder sb = new StringBuilder();
                sb.append("\n\nRELEVANT MEMORY\n");
                sb.append("Facts the user has told you to remember that may be ");
                sb.append("relevant to this turn:\n");
                int kept = 0;
                for (VectorStore.Entry e : hits) {
                    if (e.score < 0.15) continue;
                    String cat = catMap.get(e.text);
                    sb.append("- [").append(cat == null ? "Misc" : cat).append("] ")
                      .append(e.text).append("\n");
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

    public boolean isEmpty() { return load().isEmpty(); }
    public int vectorCount() { return vectors.count(); }
}
