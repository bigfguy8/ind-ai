package com.nova.assistant;

import android.app.Activity;
import android.content.Intent;
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

    private final Activity activity;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean listening = false;
    private Listener listener;
    private int utteranceCounter = 0;
    private String lastUtteranceId = null;

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

    public void startListening(Listener l) {
        this.listener = l;
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            l.onRecognizeError("Speech recognition not available.");
            return;
        }
        if (listening) return;
        stopSpeaking();
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(recognitionListener);
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        recognizer.startListening(intent);
    }

    public void stopListening() {
        if (recognizer != null && listening) {
            recognizer.stopListening();
        }
        listening = false;
    }

    public void speak(String text) {
        if (!ttsReady || text == null) return;
        String clean = stripMarkdown(text);
        if (clean.trim().isEmpty()) return;
        utteranceCounter++;
        lastUtteranceId = "nova-" + utteranceCounter;
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
            recognizer.destroy();
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
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { listening = false; }

        @Override
        public void onError(int error) {
            listening = false;
            if (listener != null) listener.onRecognizeError(errorToString(error));
        }

        @Override
        public void onResults(Bundle results) {
            listening = false;
            ArrayList<String> list = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty() && listener != null) {
                listener.onFinal(list.get(0));
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
