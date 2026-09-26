package com.indai.assistant;

public final class FailureClassifier {

    public enum Type {
        TRANSIENT, RATE_LIMIT, PERMANENT, PERMISSION, UNKNOWN
    }

    public static Type classify(String result) {
        if (result == null || result.isEmpty()) return Type.UNKNOWN;
        String r = result.toLowerCase();

        if (r.contains("429") || r.contains("rate limit")
                || r.contains("too many requests")) return Type.RATE_LIMIT;

        if (r.contains("accessibility")
                && (r.contains("off") || r.contains("not enabled")
                    || r.contains("disconnected") || r.contains("needs")))
            return Type.PERMISSION;
        if (r.contains("permission denied") || r.contains("permission was denied"))
            return Type.PERMISSION;
        if (r.contains("__blocked_")) return Type.PERMISSION;

        if (r.contains("app not installed") || r.contains("no dialer")
                || r.contains("no sms app") || r.contains("no browser")
                || r.contains("no maps") || r.contains("no music")
                || r.contains("no share") || r.contains("could not find")
                || r.contains("no focused") || r.contains("no scrollable")
                || r.contains("unknown tool") || r.contains("refused:"))
            return Type.PERMANENT;

        if (r.contains("401") || r.contains("unauthorized")
                || r.contains("invalid token") || r.contains("token not provided"))
            return Type.PERMANENT;

        if (r.contains("timeout") || r.contains("timed out")
                || r.contains("socketexception") || r.contains("connectexception")
                || r.contains("unknownhost") || r.contains("network")
                || r.contains("http 500") || r.contains("http 502")
                || r.contains("http 503") || r.contains("http 504"))
            return Type.TRANSIENT;

        if (r.contains("tool produced no result")) return Type.TRANSIENT;

        return Type.UNKNOWN;
    }

    public static long retryDelayMs(Type type) {
        switch (type) {
            case TRANSIENT:  return 1500L;
            case RATE_LIMIT: return 8000L;
            case UNKNOWN:    return 2000L;
            default:         return 0L;
        }
    }

    public static boolean isRetryable(Type type) {
        return type == Type.TRANSIENT || type == Type.RATE_LIMIT || type == Type.UNKNOWN;
    }

    public static boolean shouldReplan(Type type) {
        return type == Type.PERMANENT || type == Type.UNKNOWN || type == Type.PERMISSION;
    }

    public static String humanMessage(Type type, String raw) {
        switch (type) {
            case RATE_LIMIT: return "Rate limit hit. Wait a minute or switch provider.";
            case PERMISSION: return "Needs your action: " + trim(raw);
            case PERMANENT:  return "Cannot complete: " + trim(raw);
            case TRANSIENT:  return "Network issue — will retry. " + trim(raw);
            default:         return trim(raw);
        }
    }

    private static String trim(String raw) {
        if (raw == null) return "unknown error";
        String r = raw.replace('\n', ' ').trim();
        if (r.length() > 120) r = r.substring(0, 117) + "...";
        return r;
    }

    private FailureClassifier() {}
}
