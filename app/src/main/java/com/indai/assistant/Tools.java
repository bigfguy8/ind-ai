package com.indai.assistant;

import org.json.JSONObject;

public final class Tools {

    public static final String OPEN_APP           = "open_app";
    public static final String OPEN_URL           = "open_url";
    public static final String CLICK_TEXT         = "click_text";
    public static final String CLICK_DESCRIPTION  = "click_description";
    public static final String TYPE_TEXT          = "type_text";
    public static final String SCROLL             = "scroll";
    public static final String PRESS_BACK         = "press_back";
    public static final String PRESS_HOME         = "press_home";
    public static final String READ_SCREEN        = "read_screen";
    public static final String SET_REMINDER       = "set_reminder";
    public static final String LIST_REMINDERS     = "list_reminders";
    public static final String CANCEL_REMINDER    = "cancel_reminder";
    public static final String SEE_SCREEN         = "see_screen";
    public static final String LIST_TAPPABLE      = "list_tappable";
    public static final String CLICK_AT           = "click_at";

    // New in Round 4
    public static final String SEND_SMS           = "send_sms";
    public static final String MAKE_CALL          = "make_call";
    public static final String SET_ALARM          = "set_alarm";
    public static final String NAVIGATE_TO        = "navigate_to";
    public static final String SHARE_TEXT         = "share_text";
    public static final String SEARCH_WEB         = "search_web";
    public static final String PLAY_MUSIC         = "play_music";
    public static final String GET_TIME           = "get_time";

    public static final String SCHEMA_PROMPT =
        "\n\nDEVICE CONTROL TOOLS\n" +
        "You can request device actions by emitting a single JSON block exactly like this:\n" +
        "<tool_call>{\"name\":\"open_app\",\"arguments\":{\"package\":\"com.android.settings\"}}</tool_call>\n" +
        "Emit at most one tool_call per message. After a tool result comes back, summarize what happened.\n" +
        "Available tools:\n" +
        "- open_app(package)  open an installed app by its package name\n" +
        "- open_url(url)  open a URL in the default browser\n" +
        "- click_text(text)  tap the first visible element containing this text\n" +
        "- click_description(description)  tap the first element with this accessibility description\n" +
        "- click_at(x, y)  tap exact screen coordinates\n" +
        "- type_text(text)  type into the currently focused editable field\n" +
        "- scroll(direction)  direction is up or down\n" +
        "- press_back()  press the Android back button\n" +
        "- press_home()  press the Android home button\n" +
        "- read_screen()  read visible text on the current screen\n" +
        "- list_tappable()  list every tappable element with its screen coordinates\n" +
        "- see_screen()  capture a screenshot and describe what is visually on screen\n" +
        "- set_reminder(when, text)  schedule a reminder. when is ISO time or +15m/+2h/+1d\n" +
        "- list_reminders()  list scheduled reminders\n" +
        "- cancel_reminder(id)  cancel a reminder by its id\n" +
        "- send_sms(phone, message)  open SMS composer with number and text. Requires confirmation.\n" +
        "- make_call(phone)  open dialer with number. Requires confirmation.\n" +
        "- set_alarm(time, label)  set an alarm. time is HH:MM or +15m/+2h\n" +
        "- navigate_to(location)  open Maps directions to a location\n" +
        "- share_text(text)  open Android share sheet with this text\n" +
        "- search_web(query)  open a Google search for the query\n" +
        "- play_music(query)  play a song/artist in the default music app. Omit query to just open the player\n" +
        "- get_time()  current date and time\n" +
        "Rules:\n" +
        "- When the user asks what is on screen, or references something visually, call see_screen first.\n" +
        "- When the user asks to tap something with no clear text, call list_tappable then click_at.\n" +
        "- Only use a tool when the user explicitly asks you to perform a device action.\n" +
        "- Never invent tools. Never output shell commands.\n" +
        "- If a task needs several steps, do them one at a time and wait for each result.\n" +
        "- If a tool is refused or fails, tell the user plainly.";

    public static class Call {
        public final String name;
        public final JSONObject args;
        public Call(String name, JSONObject args) {
            this.name = name;
            this.args = args;
        }
    }

    public static boolean isKnown(String name) {
        if (name == null) return false;
        switch (name) {
            case OPEN_APP:
            case OPEN_URL:
            case CLICK_TEXT:
            case CLICK_DESCRIPTION:
            case TYPE_TEXT:
            case SCROLL:
            case PRESS_BACK:
            case PRESS_HOME:
            case READ_SCREEN:
            case SET_REMINDER:
            case LIST_REMINDERS:
            case CANCEL_REMINDER:
            case SEE_SCREEN:
            case LIST_TAPPABLE:
            case CLICK_AT:
            case SEND_SMS:
            case MAKE_CALL:
            case SET_ALARM:
            case NAVIGATE_TO:
            case SHARE_TEXT:
            case SEARCH_WEB:
            case PLAY_MUSIC:
            case GET_TIME:
                return true;
            default:
                return false;
        }
    }

    public static boolean requiresAccessibility(String name) {
        if (name == null) return false;
        switch (name) {
            case CLICK_TEXT:
            case CLICK_DESCRIPTION:
            case CLICK_AT:
            case TYPE_TEXT:
            case SCROLL:
            case PRESS_BACK:
            case PRESS_HOME:
            case READ_SCREEN:
            case SEE_SCREEN:
            case LIST_TAPPABLE:
                return true;
            default:
                return false;
        }
    }

    /** Build the schema prompt dynamically based on accessibility state. */
    public static String buildSchema(boolean accessibilityOn) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nDEVICE CONTROL TOOLS\n");
        sb.append("You can request device actions by emitting a single JSON block exactly like this:\n");
        sb.append("<tool_call>{\"name\":\"open_app\",\"arguments\":{\"package\":\"com.android.settings\"}}</tool_call>\n");
        sb.append("Emit at most one tool_call per message. After a tool result comes back, summarize what happened.\n");

        if (accessibilityOn) {
            sb.append("\nSCREEN CONTROL (accessibility enabled):\n");
            sb.append("- click_text(text)  tap the first visible element containing this text\n");
            sb.append("- click_description(description)  tap the first element with this accessibility description\n");
            sb.append("- click_at(x, y)  tap exact screen coordinates\n");
            sb.append("- type_text(text)  type into the currently focused editable field\n");
            sb.append("- scroll(direction)  direction is up or down\n");
            sb.append("- press_back()  press the Android back button\n");
            sb.append("- press_home()  press the Android home button\n");
            sb.append("- read_screen()  read visible text on the current screen\n");
            sb.append("- list_tappable()  list every tappable element with its screen coordinates\n");
            sb.append("- see_screen()  capture a screenshot and describe what is visually on screen\n");
        } else {
            sb.append("\nSCREEN CONTROL: DISABLED (user has not enabled accessibility).\n");
            sb.append("Do NOT attempt click_text, click_description, click_at, type_text, scroll, ");
            sb.append("press_back, press_home, read_screen, list_tappable, or see_screen.\n");
            sb.append("If a task requires these, tell the user to enable accessibility in Ind AI settings.\n");
        }

        sb.append("\nDEVICE ACTIONS (no accessibility needed):\n");
        sb.append("- open_app(package)  open an installed app by its package name\n");
        sb.append("- open_url(url)  open a URL in the default browser\n");
        sb.append("- send_sms(phone, message)  open SMS composer. Requires confirmation.\n");
        sb.append("- make_call(phone)  open dialer. Requires confirmation.\n");
        sb.append("- set_alarm(time, label)  set an alarm. time is HH:MM or +15m/+2h\n");
        sb.append("- navigate_to(location)  open Maps directions\n");
        sb.append("- share_text(text)  open Android share sheet\n");
        sb.append("- search_web(query)  open a Google search\n");
        sb.append("- play_music(query)  play a song in the default music app\n");
        sb.append("- get_time()  current date and time\n");
        sb.append("- set_reminder(when, text)  schedule a reminder (ISO time or +15m/+2h)\n");
        sb.append("- list_reminders()  list scheduled reminders\n");
        sb.append("- cancel_reminder(id)  cancel a reminder by its id\n");

        sb.append("\nRules:\n");
        sb.append("- Only use a tool when the user explicitly asks for a device action.\n");
        sb.append("- Never invent tools. Never output shell commands.\n");
        sb.append("- If a tool fails, tell the user plainly.\n");
        return sb.toString();
    }

    public static boolean isDangerous(String name) {
        if (name == null) return false;
        return TYPE_TEXT.equals(name)
                || SEND_SMS.equals(name)
                || MAKE_CALL.equals(name);
    }

    private Tools() {}
}
