package com.nova.assistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VectorStore extends SQLiteOpenHelper {

    private static final String DB = "nova_vectors.db";
    private static final int VERSION = 1;

    public VectorStore(Context c) {
        super(c, DB, null, VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "text TEXT NOT NULL UNIQUE," +
                "vector TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS memories");
        onCreate(db);
    }

    public static class Entry {
        public final int id;
        public final String text;
        public final float[] vector;
        public double score;
        public Entry(int id, String text, float[] vector) {
            this.id = id; this.text = text; this.vector = vector;
        }
    }

    public synchronized void add(String text, float[] vec) {
        if (text == null || text.trim().isEmpty() || vec == null) return;
        String clean = text.trim();
        if (hasText(clean)) return;
        ContentValues v = new ContentValues();
        v.put("text", clean);
        v.put("vector", toJson(vec));
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                "memories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized boolean hasText(String text) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM memories WHERE text = ? LIMIT 1",
                new String[]{text});
        boolean found = c.moveToFirst();
        c.close();
        return found;
    }

    public synchronized void remove(int id) {
        getWritableDatabase().delete("memories", "id = ?",
                new String[]{String.valueOf(id)});
    }

    public synchronized void removeByText(String text) {
        if (text == null) return;
        getWritableDatabase().delete("memories", "text = ?", new String[]{text});
    }

    public synchronized int count() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM memories", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public synchronized List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, text, vector FROM memories ORDER BY id DESC", null);
        while (c.moveToNext()) {
            out.add(new Entry(c.getInt(0), c.getString(1), fromJson(c.getString(2))));
        }
        c.close();
        return out;
    }

    public synchronized List<Entry> search(float[] query, int k) {
        List<Entry> all = all();
        List<Entry> withVec = new ArrayList<>();
        for (Entry e : all) {
            if (e.vector != null && e.vector.length == query.length) {
                e.score = EmbeddingClient.cosine(query, e.vector);
                withVec.add(e);
            }
        }
        Collections.sort(withVec, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return Double.compare(b.score, a.score);
            }
        });
        if (withVec.size() > k) return new ArrayList<>(withVec.subList(0, k));
        return withVec;
    }

    public synchronized void clear() {
        getWritableDatabase().execSQL("DELETE FROM memories");
    }

    private static String toJson(float[] v) {
        try {
            JSONArray a = new JSONArray();
            for (float x : v) a.put((double) x);
            return a.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static float[] fromJson(String s) {
        try {
            JSONArray a = new JSONArray(s);
            float[] v = new float[a.length()];
            for (int i = 0; i < a.length(); i++) v[i] = (float) a.getDouble(i);
            return v;
        } catch (Exception e) {
            return null;
        }
    }
}
