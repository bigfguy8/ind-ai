package com.nova.assistant;

public class MemoryEntry {

    public static final String CAT_PEOPLE = "People";
    public static final String CAT_PLACES = "Places";
    public static final String CAT_PREFS = "Preferences";
    public static final String CAT_PROJECTS = "Projects";
    public static final String CAT_GOALS = "Goals";
    public static final String CAT_MISC = "Misc";

    public static final String[] CATEGORIES = {
        CAT_PEOPLE, CAT_PLACES, CAT_PREFS,
        CAT_PROJECTS, CAT_GOALS, CAT_MISC
    };

    public String text;
    public String category;
    public long createdAt;

    public MemoryEntry(String text, String category, long createdAt) {
        this.text = text;
        this.category = normalizeCategory(category);
        this.createdAt = createdAt;
    }

    public static String normalizeCategory(String c) {
        if (c == null) return CAT_MISC;
        for (String cat : CATEGORIES) {
            if (cat.equalsIgnoreCase(c.trim())) return cat;
        }
        return CAT_MISC;
    }

    public static String emojiFor(String category) {
        switch (category) {
            case CAT_PEOPLE: return "👤";
            case CAT_PLACES: return "📍";
            case CAT_PREFS: return "⚙";
            case CAT_PROJECTS: return "📋";
            case CAT_GOALS: return "🎯";
            default: return "💬";
        }
    }
}
