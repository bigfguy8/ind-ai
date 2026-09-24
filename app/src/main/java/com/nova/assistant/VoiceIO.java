package com.nova.assistant;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class VoiceIO implements TextToSpeech.OnInitListener {

    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onRecognizeError(String message);
        void onSpeechDone();
    }

    // Extended silence tolerance
    private static final long SILENCE_LEN_MS = 10000L;
    private static final long POSSIBLY_SILENCE_LEN_MS = 6000L;
    private static final long MIN_LEN_MS = 1500L;
    private static final int MAX_AUTO_RETRIES = 2;

    // Voice cancel words — any of these in the partial triggers stop
    private static final List<String> CANCEL_WORDS = Arrays.asList(
            "stop", "cancel", "never mind", "nevermind", "shut up", "quiet",
            "বন্ধ", "থামো", "থাম", "চুপ", "बंद", "रुको");

    private final Activity activity;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private ToneGenerator tone;
    private boolean ttsReady = false;
    private boolean listening = false;
    private boolean speaking = false;
    private Listener listener;
    private int utteranceCounter = 0;
    private String lastUtteranceId = null;
    private int autoRetriesLeft = MAX_AUTO_RETRIES;

    public VoiceIO(Activity activity) {
        this.activity = activity;
        this.tts = new TextToSpeech(activity, this);
        try {
            // Stream-agnostic beep — short, low volume
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40);
        } catch (Exception ignored) {
            tone = null;
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    speaking = true;
                }
                @Override public void onError(String utteranceId) {
                    speaking = false;
                }
                @Override public void onDone(String utteranceId) {
                    if (utteranceId.equals(lastUtteranceId)) {
                        speaking = false;
                        if (listener != null) {
                            activity.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (listener != null) listener.onSpeechDone();
                                }
                            });
                        }
                    }
                }
            });
            ttsReady = true;
        }
    }

    public boolean isTtsReady() { return ttsReady; }
    public boolean isListening() { return listening; }
    public boolean isSpeaking() { return speaking; }

    public static boolean isRecognitionAvailable(Activity a) {
        return SpeechRecognizer.isRecognitionAvailable(a);
    }

    // ------------------------------------------------------------------
    //  Listening
    // ------------------------------------------------------------------

    public void startListening(Listener l) {
        this.listener = l;
        this.autoRetriesLeft = MAX_AUTO_RETRIES;
        startListeningInternal(false);
    }

    private void startListeningInternal(boolean isRetry) {
        if (listener == null) return;

        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            listener.onRecognizeError(
                    "No speech recognition service found. Install the Google app "
                    + "or your phone's speech service, then try again.");
            return;
        }
        if (listening && !isRetry) return;

        stopSpeaking();
        if (recognizer == null) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
                recognizer.setRecognitionListener(recognitionListener);
            } catch (Exception e) {
                listener.onRecognizeError("Speech recognizer unavailable: " + e.getMessage());
                return;
            }
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_LEN_MS);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLY_SILENCE_LEN_MS);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MIN_LEN_MS);

        // Wake chime — quick high beep so user knows mic is live
        if (!isRetry) playWakeChime();

        listening = true;
        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            listening = false;
            listener.onRecognizeError("Could not start listening: " + e.getMessage());
        }
    }

    public void stopListening() {
        if (recognizer != null && listening) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
        listening = false;
    }

    // ------------------------------------------------------------------
    //  Speaking
    // ------------------------------------------------------------------

    public void speak(String text) {
        if (!ttsReady || text == null) return;
        String clean = stripMarkdown(text);
        if (clean.trim().isEmpty()) return;
        utteranceCounter++;
        lastUtteranceId = "indai-" + utteranceCounter;
        speaking = true;
        tts.speak(clean, TextToSpeech.QUEUE_ADD, null, lastUtteranceId);
    }

    /** Speak a short confirmation (single beep-free phrase, clears queue first). */
    public void speakShort(String text) {
        if (!ttsReady || text == null) return;
        tts.stop();
        String clean = stripMarkdown(text);
        if (clean.trim().isEmpty()) return;
        utteranceCounter++;
        lastUtteranceId = "indai-" + utteranceCounter;
        speaking = true;
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, lastUtteranceId);
    }

    public void stopSpeaking() {
        if (ttsReady) {
            tts.stop();
            speaking = false;
        }
    }

    // ------------------------------------------------------------------
    //  Audio cues
    // ------------------------------------------------------------------

    private void playWakeChime() {
        if (tone == null) return;
        try {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
        } catch (Exception ignored) {}
    }

    public void playSuccessCue() {
        if (tone == null) return;
        try {
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 100);
        } catch (Exception ignored) {}
    }

    public void playErrorCue() {
        if (tone == null) return;
        try {
            tone.startTone(ToneGenerator.TONE_PROP_NACK, 150);
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    //  Shutdown
    // ------------------------------------------------------------------

    public void shutdown() {
        listening = false;
        speaking = false;
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (tone != null) {
            try { tone.release(); } catch (Exception ignored) {}
            tone = null;
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static boolean isCancelPhrase(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        // Strip trailing punctuation
        while (t.length() > 0 && ".,!?".indexOf(t.charAt(t.length() - 1)) >= 0) {
            t = t.substring(0, t.length() - 1).trim();
        }
        for (String w : CANCEL_WORDS) {
            if (t.equals(w)) return true;
        }
        // Also catch "<word>." style single-word commands
        for (String w : CANCEL_WORDS) {
            if (t.startsWith(w + " ") && t.length() < w.length() + 6) return true;
        }
        return false;
    }

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {
            autoRetriesLeft = MAX_AUTO_RETRIES;
        }
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { listening = false; }

        @Override
        public void onError(int error) {
            listening = false;

            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    || error == SpeechRecognizer.ERROR_NO_MATCH) {
                if (autoRetriesLeft > 0 && listener != null) {
                    autoRetriesLeft--;
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            startListeningInternal(true);
                        }
                    });
                    return;
                }
                if (listener != null) listener.onRecognizeError("Didn't catch that");
                return;
            }

            if (error == SpeechRecognizer.ERROR_CLIENT
                    && autoRetriesLeft > 0 && listener != null) {
                autoRetriesLeft--;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (recognizer != null) {
                            try { recognizer.destroy(); } catch (Exception ignored) {}
                            recognizer = null;
                        }
                        startListeningInternal(true);
                    }
                });
                return;
            }

            if (listener != null) listener.onRecognizeError(errorToString(error));
        }

        @Override
        public void onResults(Bundle results) {
            listening = false;
            ArrayList<String> list = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (list == null || list.isEmpty()) {
                if (listener != null) listener.onRecognizeError("Didn't catch that");
                return;
            }
            String finalText = list.get(0);
            // Check for cancel word
            if (isCancelPhrase(finalText)) {
                stopSpeaking();
                if (listener != null) listener.onSpeechDone();
                return;
            }
            if (listener != null) listener.onFinal(finalText);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> list = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (list == null || list.isEmpty()) return;
            String partial = list.get(0);
            // Live cancel detection — if user says "stop" while we're speaking,
            // interrupt immediately.
            if (speaking && isCancelPhrase(partial)) {
                stopSpeaking();
                stopListening();
                if (listener != null) listener.onSpeechDone();
                return;
            }
            if (listener != null) listener.onPartial(partial);
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    };

    private static String errorToString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "Didn't catch that";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech detected";
            default: return "Recognition error (" + error + ")";
        }
    }

    private static String stripMarkdown(String s) {
        if (s == null) return "";
        String r = s;
        r = r.replaceAll("(?s)```[a-zA-Z0-9+#-]*\\n?.*?```", " code block ");
        r = r.replaceAll("`([^`]+)`", "$1");
        r = r.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        r = r.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "$1");
        r = r.replaceAll("__(.+?)__", "$1");
        r = r.replaceAll("(?m)^#{1,6}\\s+", "");
        r = r.replaceAll("(?m)^\\s*[-*]\\s+", "");
        r = r.replaceAll("\\n{3,}", "\n\n");
        return r.trim();
    }
}
