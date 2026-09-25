package com.indai.assistant;

import android.content.Context;
import android.content.SharedPreferences;

public class IdentityStore {

    public static final String TONE_FORMAL = "Formal";
    public static final String TONE_CASUAL = "Casual";
    public static final String TONE_CONCISE = "Concise";
    public static final String TONE_DETAILED = "Detailed";

    public static final String LANG_AUTO = "Auto";
    public static final String LANG_ENGLISH = "English";
    public static final String LANG_BENGALI = "Bengali";
    public static final String LANG_HINDI = "Hindi";

    public static final String GREETING_SIMPLE = "Simple";
    public static final String GREETING_WARM = "Warm";
    public static final String GREETING_PROFESSIONAL = "Professional";

    private static final String PREFS = "nova_identity";

    private final SharedPreferences prefs;

    public IdentityStore(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getUserName() { return prefs.getString("user_name", ""); }
    public String getAssistantName() { return prefs.getString("assistant_name", "Ind AI"); }
    public String getTone() { return prefs.getString("tone", TONE_CASUAL); }
    public String getLanguage() { return prefs.getString("language", LANG_AUTO); }
    public String getGreeting() { return prefs.getString("greeting", GREETING_WARM); }
    public String getUserRole() { return prefs.getString("user_role", ""); }

    public void setUserName(String v) { prefs.edit().putString("user_name", v).apply(); }
    public void setAssistantName(String v) { prefs.edit().putString("assistant_name", v).apply(); }
    public void setTone(String v) { prefs.edit().putString("tone", v).apply(); }
    public void setLanguage(String v) { prefs.edit().putString("language", v).apply(); }
    public void setGreeting(String v) { prefs.edit().putString("greeting", v).apply(); }
    public void setUserRole(String v) { prefs.edit().putString("user_role", v).apply(); }

    public String buildPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nUSER PROFILE\n");

        String userName = getUserName().trim();
        if (!userName.isEmpty()) {
            sb.append("- The user's name is ").append(userName)
              .append(". Address them by name when natural, but not in every reply.\n");
        }

        String role = getUserRole().trim();
        if (!role.isEmpty()) {
            sb.append("- The user's role or context: ").append(role).append("\n");
        }

        String assistant = getAssistantName().trim();
        if (!assistant.isEmpty() && !"Ind AI".equalsIgnoreCase(assistant)) {
            sb.append("- You are called ").append(assistant).append(".\n");
        }

        String tone = getTone();
        sb.append("- Tone: ");
        switch (tone) {
            case TONE_FORMAL:
                sb.append("professional and respectful. Avoid slang, emojis, and casual phrasing.\n");
                break;
            case TONE_CONCISE:
                sb.append("extremely concise. Minimum words. No filler, no explanation unless asked.\n");
                break;
            case TONE_DETAILED:
                sb.append("thorough and explanatory. Provide context, examples, and reasoning when useful.\n");
                break;
            case TONE_CASUAL:
            default:
                sb.append("warm, relaxed, and conversational. Contractions are fine.\n");
                break;
        }

        String lang = getLanguage();
        switch (lang) {
            case LANG_ENGLISH:
                sb.append("- Always respond in English, even if the user writes in another language.\n");
                break;
            case LANG_BENGALI:
                sb.append("- Always respond in Bengali (বাংলা), even if the user writes in another language.\n");
                break;
            case LANG_HINDI:
                sb.append("- Always respond in Hindi (हिन्दी), even if the user writes in another language.\n");
                break;
            case LANG_AUTO:
            default:
                sb.append("- Respond in the same language the user writes in. If mixed, use the primary one.\n");
                break;
        }

        String greeting = getGreeting();
        sb.append("- Greeting style: ");
        switch (greeting) {
            case GREETING_SIMPLE:
                sb.append("plain and direct. Say something like \"Ready.\" or \"What do you need?\"\n");
                break;
            case GREETING_PROFESSIONAL:
                sb.append("courteous. Say something like \"Good day. How may I assist?\"\n");
                break;
            case GREETING_WARM:
            default:
                sb.append("friendly. Use time-aware greetings like \"Morning, [name].\" when it's the first message.\n");
                break;
        }

        sb.append("- Only greet on the first message of a session. Do not repeat greetings every turn.\n");
        return sb.toString();
    }

    public boolean isDefault() {
        return getUserName().trim().isEmpty()
                && getUserRole().trim().isEmpty()
                && "Ind AI".equalsIgnoreCase(getAssistantName());
    }
}
