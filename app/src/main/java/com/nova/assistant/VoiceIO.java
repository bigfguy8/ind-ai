package com.nova.assistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceIO implements TextToSpeech.OnInitListener {

    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onRecognizeError(String message);
        void onSpeechDone();
    }

    // Give the user ~8 seconds of silence before giving up.
    private static final long SILENCE_LEN_MS = 8000L;
    private static final long POSSIBLY_SILENCE_LEN_MS = 5000L;
    private static final long MIN_LEN_MS = 1500L;
    private static final int MAX_AUTO_RETRIES = 2;

    private final Activity activity;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean listening = false;
    private Listener listener;
    private int utteranceCounter = 0;
    private String lastUtteranceId = null;
    private int autoRetriesLeft = MAX_AUTO_RETRIES;

    public VoiceIO(Activity activity) {
        this.activity = activity;
        this.tts = new TextToSpeech(activity, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onError(String utteranceId) {}
                @Override public void onDone(String utteranceId) {
                    if (utteranceId.equals(lastUtteranceId) && listener != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (listener != null) listener.onSpeechDone();
                            }
                        });
                    }
                }
            });
            ttsReady = true;
        }
    }

    public boolean isTtsReady() { return ttsReady; }
    public boolean isListening() { return listening; }

    public static boolean isRecognitionAvailable(Activity a) {
        return SpeechRecognizer.isRecognitionAvailable(a);
    }

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
                    + "(com.google.android.googlequicksearchbox) from the Play Store "
                    + "or your phone's app store, then try again.");
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

        // Extended silence tolerance — the defaults cut off at ~2s of quiet.
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_LEN_MS);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLY_SILENCE_LEN_MS);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MIN_LEN_MS);

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

    public void speak(String text) {
        if (!ttsReady || text == null) return;
        String clean = stripMarkdown(text);
        if (clean.trim().isEmpty()) return;
        utteranceCounter++;
        lastUtteranceId = "indai-" + utteranceCounter;
        tts.speak(clean, TextToSpeech.QUEUE_ADD, null, lastUtteranceId);
    }

    public void stopSpeaking() {
        if (ttsReady) tts.stop();
    }

    public boolean isSpeaking() {
        return ttsReady && tts.isSpeaking();
    }

    public void shutdown() {
        listening = false;
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {
            // Reset retries once the user actually starts speaking
            autoRetriesLeft = MAX_AUTO_RETRIES;
        }
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            listening = false;
        }

        @Override
        public void onError(int error) {
            listening = false;

            // Silent timeouts: retry automatically without bothering the user.
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
                if (listener != null) {
                    listener.onRecognizeError("Didn't catch that");
                }
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
            if (list != null && !list.isEmpty() && listener != null) {
                listener.onFinal(list.get(0));
            } else if (listener != null) {
                listener.onRecognizeError("Didn't catch that");
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> list = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty() && listener != null) {
                listener.onPartial(list.get(0));
            }
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
