package com.nova.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends Activity {

    private static final int MAX_TOOL_DEPTH = 4;
    private static final int REQ_MIC = 1001;
    private static final int REQ_PICK_IMAGE = 1002;

    private android.net.Uri pendingImageUri = null;
    private LinearLayout imagePreviewRow;
    private ImageView imagePreviewThumb;

    private LinearLayout messagesList;
    private ScrollView scroll;
    private EditText input;
    private View sendButton;
    private View micButton;
    private ImageView micIcon;
    private LinearLayout emptyState;
    private ImageView speakerIcon;

    private SecureKeyStore keyStore;
    private ChatStore chatStore;
    private MemoryStore memoryStore;
    private IdentityStore identityStore;
    private ApiConfig config;
    private final AiClient ai = new AiClient();
    private VoiceIO voice;

    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView streamingBubble;
    private StringBuilder streamingText;
    private int toolDepth = 0;

    private boolean speakerOn = false;
    private boolean listening = false;
    private boolean speaking = false;
    private String lastUserMessage = null;
    private int lastAiHistoryIndex = -1;

    private ImageView agentToggleIcon;
    private boolean agentOn = false;
    private AgentSession currentAgent;
    private AgentRenderer.PlanCard currentPlanCard;

    private final VoiceIO.Listener voiceListener = new VoiceIO.Listener() {
        @Override public void onPartial(final String text) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (!listening) return;
                    input.setText(text);
                    input.setSelection(input.getText().length());
                }
            });
        }

        @Override public void onFinal(final String text) {
            main.post(new Runnable() {
                @Override public void run() {
                    listening = false;
                    updateMicButton();
                    input.setText("");
                    String trimmed = text == null ? "" : text.trim();
                    if (!trimmed.isEmpty()) {
                        sendText(trimmed);
                    } else {
                        input.setHint("Ask Ind AI...");
                    }
                }
            });
        }

        @Override public void onRecognizeError(final String message) {
            main.post(new Runnable() {
                @Override public void run() {
                    listening = false;
                    updateMicButton();
                    input.setHint("Ask Ind AI...");
                    if (!"Didn't catch that".equals(message) &&
                        !"No speech detected".equals(message)) {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @Override public void onSpeechDone() {
            main.post(new Runnable() {
                @Override public void run() {
                    speaking = false;
                    if (speakerOn && config.apiKey != null && !config.apiKey.isEmpty()) {
                        main.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (speakerOn) startListening();
                            }
                        }, 350);
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        keyStore = new SecureKeyStore(this);
        config = keyStore.load();
        chatStore = new ChatStore(this);
        memoryStore = new MemoryStore(this, config);
        identityStore = new IdentityStore(this);
        voice = new VoiceIO(this);

        JSONArray saved = chatStore.load();
        if (saved.length() > 0) {
            ai.setHistory(saved);
            applySystemPrompt(false);
        } else {
            applySystemPrompt(true);
        }

        buildInterface();
        restoreHistory();
        updateEmptyState();

        if (FirstRunSetup.needsSetup(config)) {
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    FirstRunSetup.show(MainActivity.this, new FirstRunSetup.OnReady() {
                        @Override public void onReady() {
                            config = keyStore.load();
                            applySystemPrompt(true);
                            appendMessage("Provider configured. Ready.", Sender.AI);
                        }
                    });
                }
            }, 300);
        }
    }

    private void applySystemPrompt(boolean fresh) {
        String identityBlock = identityStore == null ? "" : identityStore.buildPromptBlock();
        String full = config.systemPrompt
                + identityBlock
                + memoryStore.asSystemBlock()
                + Tools.SCHEMA_PROMPT;
        if (fresh) {
            ai.reset(new ApiConfig(config.endpoint, config.apiKey, config.model, full));
        } else {
            ai.updateSystem(full);
        }
    }

    // ==================================================================
    //  UI
    // ==================================================================

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setFitsSystemWindows(true);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int padH = Theme.dp(this, Theme.S5);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(padH, Theme.dp(this, Theme.S5), padH, Theme.dp(this, Theme.S3));

        TextView brand = new TextView(this);
        brand.setText("I N D  A I");
        brand.setTextColor(Theme.TEXT_PRIMARY);
        brand.setTextSize(Theme.T_TITLE);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setLetterSpacing(0.28f);
        brand.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        brand.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showSettings();
                return true;
            }
        });
        header.addView(brand);

        header.addView(iconButton(R.drawable.ic_search, v -> showSearch()));
        header.addView(spacer(Theme.S1));

        View agentBtn = iconButton(R.drawable.ic_bolt, v -> toggleAgentMode());
        agentToggleIcon = (ImageView) ((ViewGroup) agentBtn).getChildAt(0);
        header.addView(agentBtn);
        header.addView(spacer(Theme.S1));

        View speakerBtn = iconButton(R.drawable.ic_speaker_off, v -> toggleSpeaker());
        speakerIcon = (ImageView) ((ViewGroup) speakerBtn).getChildAt(0);
        header.addView(speakerBtn);
        header.addView(spacer(Theme.S1));

        header.addView(iconButton(R.drawable.ic_clear, v -> confirmClear()));

        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout msgFrame = new FrameLayout(this);
        column.addView(msgFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Theme.dp(this, Theme.S2), 0, Theme.dp(this, Theme.S2));
        msgFrame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        messagesList = new LinearLayout(this);
        messagesList.setOrientation(LinearLayout.VERTICAL);
        int side = Theme.dp(this, Theme.S4);
        messagesList.setPadding(side, Theme.dp(this, Theme.S1),
                side, Theme.dp(this, Theme.S1));
        scroll.addView(messagesList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyState = buildEmptyState();
        msgFrame.addView(emptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(padH, Theme.dp(this, Theme.S2),
                padH, Theme.dp(this, Theme.S4));

        LinearLayout inputWrap = new LinearLayout(this);
        inputWrap.setOrientation(LinearLayout.VERTICAL);
        inputWrap.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_PILL, 1f));
        int inPadH = Theme.dp(this, Theme.S4);
        int inPadV = Theme.dp(this, Theme.S2);
        inputWrap.setPadding(inPadH, inPadV, inPadH, inPadV);
        inputWrap.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        input = new EditText(this);
        input.setHint("Ask Ind AI...");
        input.setHintTextColor(Theme.TEXT_TERTIARY);
        input.setTextColor(Theme.TEXT_PRIMARY);
        input.setTextSize(Theme.T_BODY);
        input.setBackgroundColor(0x00000000);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(6);
        input.setPadding(0, Theme.dp(this, Theme.S1), 0, Theme.dp(this, Theme.S1));
        input.setOnClickListener(v -> {
            if (listening) stopListening();
            if (speaking) voice.stopSpeaking();
        });
        inputWrap.addView(input);
        composer.addView(inputWrap);

        composer.addView(spacer(Theme.S3));

        micButton = buildMicButton();
        composer.addView(micButton);

        composer.addView(spacer(Theme.S2));

        View cameraBtn = buildCameraButton();
        composer.addView(cameraBtn);

        composer.addView(spacer(Theme.S2));

        sendButton = buildSendButton();
        composer.addView(sendButton);

        imagePreviewRow = buildImagePreviewRow();
        column.addView(imagePreviewRow);

        column.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(BottomNav.build(this, BottomNav.TAB_CHAT));

        setContentView(root);
    }

    private View buildSendButton() {
        FrameLayout wrap = new FrameLayout(this);
        int size = Theme.dp(this, 52);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        wrap.setBackground(Drawables.tappable(this, Theme.PRIMARY, Theme.R_PILL));
        wrap.setElevation(Theme.dp(this, 4));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_send);
        int iSize = Theme.dp(this, 22);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(iSize, iSize);
        ilp.gravity = Gravity.CENTER;
        wrap.addView(icon, ilp);

        wrap.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (currentAgent != null) {
                currentAgent.cancel();
                appendMessage("Cancelling agent...", Sender.TOOL);
                return;
            }
            String t = input.getText().toString().trim();
            if (pendingImageUri != null) {
                input.setText("");
                sendImage(t);
                return;
            }
            if (!t.isEmpty()) {
                input.setText("");
                sendText(t);
            }
        });
        return wrap;
    }

    private View buildCameraButton() {
        FrameLayout wrap = new FrameLayout(this);
        int size = Theme.dp(this, 52);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        wrap.setBackground(Drawables.tappable(this, Theme.SURFACE, Theme.R_PILL));
        wrap.setElevation(Theme.dp(this, 2));

        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_camera);
        int iSize = Theme.dp(this, 22);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iSize, iSize);
        lp.gravity = Gravity.CENTER;
        wrap.addView(iv, lp);
        iv.setColorFilter(Theme.TEXT_SECONDARY);

        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                openImagePicker();
            }
        });
        return wrap;
    }

    private LinearLayout buildImagePreviewRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = Theme.dp(this, Theme.S5);
        int padV = Theme.dp(this, Theme.S2);
        row.setPadding(padH, padV, padH, padV);
        row.setVisibility(View.GONE);

        imagePreviewThumb = new ImageView(this);
        int thumbSize = Theme.dp(this, 56);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(thumbSize, thumbSize);
        imagePreviewThumb.setLayoutParams(tlp);
        imagePreviewThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(imagePreviewThumb);

        TextView clear = new TextView(this);
        clear.setText("  Remove");
        clear.setTextColor(Theme.ERROR);
        clear.setTextSize(Theme.T_CAPTION);
        clear.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clear.setPadding(Theme.dp(this, Theme.S3), 0, 0, 0);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                clearPendingImage();
            }
        });
        row.addView(clear);

        return row;
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Pick an image"),
                    REQ_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "No app available to pick images.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void clearPendingImage() {
        pendingImageUri = null;
        if (imagePreviewRow != null) imagePreviewRow.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_IMAGE && res == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri == null) return;
            pendingImageUri = uri;
            try {
                imagePreviewThumb.setImageURI(uri);
                imagePreviewRow.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                Toast.makeText(this, "Could not preview image.",
                        Toast.LENGTH_SHORT).show();
                pendingImageUri = null;
            }
        }
    }

    private View buildMicButton() {
        FrameLayout wrap = new FrameLayout(this);
        int size = Theme.dp(this, 52);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        wrap.setBackground(Drawables.tappable(this, Theme.SURFACE, Theme.R_PILL));
        wrap.setElevation(Theme.dp(this, 2));

        micIcon = new ImageView(this);
        micIcon.setImageResource(R.drawable.ic_mic);
        int iSize = Theme.dp(this, 22);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iSize, iSize);
        lp.gravity = Gravity.CENTER;
        wrap.addView(micIcon, lp);
        micIcon.setColorFilter(Theme.TEXT_SECONDARY);

        wrap.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listening) stopListening();
            else startListening();
        });
        return wrap;
    }

    private View iconButton(int res, View.OnClickListener click) {
        FrameLayout wrap = new FrameLayout(this);
        int size = Theme.dp(this, 40);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        wrap.setBackground(Drawables.tappable(this, Theme.SURFACE, Theme.R_PILL));

        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        int iSize = Theme.dp(this, 20);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iSize, iSize);
        lp.gravity = Gravity.CENTER;
        wrap.addView(iv, lp);
        iv.setColorFilter(Theme.TEXT_SECONDARY);

        wrap.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            click.onClick(v);
        });
        return wrap;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, dp), 1));
        return v;
    }

    private LinearLayout buildEmptyState() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(Theme.dp(this, Theme.S6), 0,
                Theme.dp(this, Theme.S6), Theme.dp(this, Theme.S6));

        TextView glyph = new TextView(this);
        glyph.setText("◈");
        glyph.setTextSize(56);
        glyph.setTextColor(Theme.PRIMARY);
        glyph.setAlpha(0.85f);
        wrap.addView(glyph);

        TextView title = new TextView(this);
        title.setText("Ready when you are");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(Theme.T_DISPLAY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Theme.dp(this, Theme.S4);
        wrap.addView(title, tlp);

        TextView sub = new TextView(this);
        sub.setText("Type, tap the mic, or toggle the speaker for hands-free conversation.");
        sub.setTextColor(Theme.TEXT_SECONDARY);
        sub.setTextSize(Theme.T_CAPTION);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Theme.dp(this, Theme.S2);
        wrap.addView(sub, slp);

        return wrap;
    }

    // ==================================================================
    //  Voice
    // ==================================================================

    private void toggleSpeaker() {
        if (!voice.isTtsReady()) {
            Toast.makeText(this, "Text-to-speech not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        speakerOn = !speakerOn;
        updateSpeakerButton();
        if (!speakerOn) {
            voice.stopSpeaking();
        }
    }

    private void updateSpeakerButton() {
        if (speakerIcon == null) return;
        speakerIcon.setImageResource(speakerOn ? R.drawable.ic_speaker : R.drawable.ic_speaker_off);
        speakerIcon.setColorFilter(speakerOn ? Theme.PRIMARY : Theme.TEXT_SECONDARY);
    }

    private void updateMicButton() {
        if (micIcon == null) return;
        if (listening) {
            micIcon.setImageResource(R.drawable.ic_stop);
            micIcon.setColorFilter(Theme.ERROR);
            input.setHint("Listening...");
            input.setEnabled(false);
        } else {
            micIcon.setImageResource(R.drawable.ic_mic);
            micIcon.setColorFilter(Theme.TEXT_SECONDARY);
            input.setEnabled(true);
            if (input.getText().toString().isEmpty()) {
                input.setHint("Ask Ind AI...");
            }
        }
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechAvailable()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Speech recognition unavailable")
                    .setMessage("Ind AI couldn't find a speech recognition service on this device.\n\n"
                            + "To enable voice input:\n"
                            + "1. Install the Google app from your app store\n"
                            + "2. Make sure 'Speech Recognition & Synthesis' is installed\n"
                            + "3. Restart Ind AI and try the mic again")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        listening = true;
        updateMicButton();
        voice.startListening(voiceListener);
    }

    private boolean SpeechAvailable() {
        return android.speech.SpeechRecognizer.isRecognitionAvailable(this);
    }

    private void stopListening() {
        if (listening) {
            voice.stopListening();
            listening = false;
            updateMicButton();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                Toast.makeText(this, "Microphone permission denied.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================================================================
    //  Messages
    // ==================================================================

    private enum Sender { USER, AI, TOOL }

    private void restoreHistory() {
        JSONArray hist = ai.getHistory();
        for (int i = 0; i < hist.length(); i++) {
            try {
                JSONObject m = hist.getJSONObject(i);
                String role = m.optString("role");
                if ("system".equals(role)) continue;
                String content = m.optString("content");
                if ("user".equals(role) && content.startsWith("TOOL_RESULT:")) {
                    appendMessage(content.substring(12).trim(), Sender.TOOL);
                } else if ("user".equals(role)) {
                    appendMessage(content, Sender.USER);
                } else {
                    appendMessage(content, Sender.AI);
                }
            } catch (Exception ignored) {}
        }
    }

    private void appendMessage(String text, Sender who) {
        View bubble = buildBubble(text, who);
        messagesList.addView(bubble);
        bubble.setAlpha(0f);
        bubble.setTranslationY(Theme.dp(this, 8));
        bubble.animate().alpha(1f).translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        updateEmptyState();
    }

    private View buildBubble(final String text, Sender who) {
        boolean user = who == Sender.USER;
        boolean tool = who == Sender.TOOL;

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(user ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cellLp.bottomMargin = Theme.dp(this, Theme.S3);
        cell.setLayoutParams(cellLp);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(user ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams lrlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lrlp.leftMargin = Theme.dp(this, Theme.S2);
        lrlp.rightMargin = Theme.dp(this, Theme.S2);
        lrlp.bottomMargin = Theme.dp(this, Theme.S1);
        labelRow.setLayoutParams(lrlp);

        TextView label = new TextView(this);
        label.setText(user ? "YOU" : tool ? "ACTION" : "IND AI");
        label.setTextSize(Theme.T_LABEL);
        label.setLetterSpacing(0.15f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextColor(user ? Theme.TEXT_TERTIARY : Theme.PRIMARY);
        labelRow.addView(label);

        TextView stamp = new TextView(this);
        stamp.setText("  " + new java.text.SimpleDateFormat("HH:mm",
                java.util.Locale.getDefault()).format(new java.util.Date()));
        stamp.setTextSize(Theme.T_LABEL - 0.5f);
        stamp.setTextColor(Theme.TEXT_TERTIARY);
        labelRow.addView(stamp);

        cell.addView(labelRow);

        TextView bubble = new TextView(this);
        if (user || tool) {
            bubble.setText(text);
        } else {
            bubble.setText(MarkdownRenderer.render(text));
        }
        bubble.setTextSize(Theme.T_BODY);
        bubble.setLineSpacing(Theme.dp(this, 3), 1f);
        int bpH = Theme.dp(this, Theme.S4);
        int bpV = Theme.dp(this, Theme.S3);
        bubble.setPadding(bpH, bpV, bpH, bpV);

        int bg = user ? Theme.BUBBLE_USER
                : tool ? Theme.SURFACE_HIGH
                : Theme.BUBBLE_AI;
        bubble.setBackground(Drawables.bubble(this, bg, user));
        bubble.setTextColor(user ? Theme.BUBBLE_USER_TEXT
                : tool ? Theme.TEXT_SECONDARY
                : Theme.TEXT_PRIMARY);
        bubble.setElevation(user ? 0 : Theme.dp(this, 1));

        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
        bubble.setMaxWidth(maxW);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (user) blp.gravity = Gravity.END;
        bubble.setLayoutParams(blp);

        bubble.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showMessageActions(text, who);
            return true;
        });

        cell.addView(bubble);
        return cell;
    }

    // ==================================================================
    //  Streaming bubble
    // ==================================================================

    private void openStreamingBubble() {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.START);
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cellLp.bottomMargin = Theme.dp(this, Theme.S3);
        cell.setLayoutParams(cellLp);

        TextView label = new TextView(this);
        label.setText("IND AI");
        label.setTextSize(Theme.T_LABEL);
        label.setLetterSpacing(0.15f);
        label.setTextColor(Theme.PRIMARY);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = Theme.dp(this, Theme.S2);
        llp.bottomMargin = Theme.dp(this, Theme.S1);
        label.setLayoutParams(llp);
        cell.addView(label);

        streamingBubble = new TextView(this);
        streamingBubble.setTextSize(Theme.T_BODY);
        streamingBubble.setLineSpacing(Theme.dp(this, 3), 1f);
        int bpH = Theme.dp(this, Theme.S4);
        int bpV = Theme.dp(this, Theme.S3);
        streamingBubble.setPadding(bpH, bpV, bpH, bpV);
        streamingBubble.setBackground(Drawables.bubble(this, Theme.BUBBLE_AI, false));
        streamingBubble.setTextColor(Theme.TEXT_PRIMARY);
        streamingBubble.setElevation(Theme.dp(this, 1));

        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
        streamingBubble.setMaxWidth(maxW);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        streamingBubble.setLayoutParams(blp);

        cell.addView(streamingBubble);

        messagesList.addView(cell);
        cell.setAlpha(0f);
        cell.animate().alpha(1f).setDuration(180).start();
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        updateEmptyState();

        streamingText = new StringBuilder();
    }

    private void closeStreamingBubble() {
        streamingBubble = null;
        streamingText = null;
    }

    private void removeStreamingBubble() {
        if (streamingBubble == null) return;
        View parent = (View) streamingBubble.getParent();
        if (parent != null && parent.getParent() instanceof ViewGroup) {
            ((ViewGroup) parent.getParent()).removeView(parent);
        }
        closeStreamingBubble();
    }

    // ==================================================================
    //  Send / turn loop
    // ==================================================================

    private void sendImage(final String prompt) {
        final android.net.Uri uri = pendingImageUri;
        if (uri == null) return;

        String displayText = (prompt == null || prompt.trim().isEmpty())
                ? "[image]" : "[image] " + prompt.trim();
        appendMessage(displayText, Sender.USER);
        clearPendingImage();

        setSending(false);
        openStreamingBubble();

        VisionClient.describe(this, config, uri, prompt, new VisionClient.Callback() {
            @Override public void onSuccess(final String reply) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (streamingBubble != null) {
                            streamingBubble.setText(MarkdownRenderer.render(reply));
                        }
                        closeStreamingBubble();
                        setSending(true);
                        scroll.post(new Runnable() {
                            @Override public void run() {
                                scroll.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                        if (speakerOn) voice.speak(reply);
                    }
                });
            }

            @Override public void onError(final String error) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (streamingBubble != null) {
                            streamingBubble.setText(MarkdownRenderer.render("! " + error));
                        }
                        closeStreamingBubble();
                        setSending(true);
                    }
                });
            }
        });
    }

    private void sendText(String text) {
        if (text == null || text.trim().isEmpty()) return;

        stopListening();
        voice.stopSpeaking();

        if (agentOn) {
            startAgentTask(text.trim());
            return;
        }

        final String userMessage = text.trim();
        lastUserMessage = userMessage;
        appendMessage(userMessage, Sender.USER);
        chatStore.save(ai.getHistory());
        toolDepth = 0;

        // Auto-extract facts in the background (only fires if the message looks factual)
        FactExtractor.extract(config, userMessage, new FactExtractor.Listener() {
            @Override public void onFacts(java.util.List<String> facts) {
                if (facts == null || facts.isEmpty()) return;
                for (String fact : facts) memoryStore.add(fact);
                main.post(new Runnable() {
                    @Override public void run() {
                        // silent — no UI bubble for auto-captured facts
                    }
                });
            }
        });

        memoryStore.relevantBlockAsync(userMessage, 4, new MemoryStore.BlockCallback() {
            @Override public void onBlock(final String block) {
                main.post(new Runnable() {
                    @Override public void run() {
                        String idBlock = identityStore == null ? "" : identityStore.buildPromptBlock();
                        String full = config.systemPrompt + idBlock + block + Tools.SCHEMA_PROMPT;
                        ai.updateSystem(full);
                        runTurn(userMessage);
                    }
                });
            }
        });
    }

    private void runTurn(final String userMessage) {
        if (toolDepth > MAX_TOOL_DEPTH) {
            appendMessage("Tool loop limit reached.", Sender.TOOL);
            setSending(true);
            return;
        }

        setSending(false);
        openStreamingBubble();

        ai.chat(config, userMessage, new AiClient.Callback() {
            @Override public void onDelta(final String piece) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (streamingBubble == null) return;
                        streamingText.append(piece);
                        streamingBubble.setText(streamingText.toString());
                        scroll.post(new Runnable() {
                            @Override public void run() {
                                scroll.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                });
            }

            @Override public void onSuccess(final String full) {
                main.post(new Runnable() {
                    @Override public void run() {
                        ToolParser.Result parsed = ToolParser.parse(full);

                        if (streamingBubble != null) {
                            if (parsed.cleanedText.isEmpty()) {
                                removeStreamingBubble();
                            } else {
                                String safe = AiClient.sanitizeIdentity(parsed.cleanedText);
                                streamingBubble.setText(MarkdownRenderer.render(safe));
                            }
                        }
                        closeStreamingBubble();
                        chatStore.save(ai.getHistory());

                        if (speakerOn && !parsed.cleanedText.isEmpty()) {
                            speaking = true;
                            voice.speak(parsed.cleanedText);
                        }

                        if (parsed.calls.isEmpty()) {
                            setSending(true);
                            scroll.post(new Runnable() {
                                @Override public void run() {
                                    scroll.fullScroll(ScrollView.FOCUS_DOWN);
                                }
                            });
                            if (!speakerOn) {
                                // no auto-listen needed
                            }
                            return;
                        }
                        handleToolCall(parsed.calls.get(0));
                    }
                });
            }

            @Override public void onError(final String error) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (streamingBubble != null) {
                            streamingBubble.setText(
                                    MarkdownRenderer.render("⚠ " + error));
                        }
                        closeStreamingBubble();
                        setSending(true);
                    }
                });
            }
        });
    }

    private void handleToolCall(final Tools.Call call) {
        ToolExecutor.execute(this, call, new ToolExecutor.Listener() {
            @Override public void onNeedsConfirmation(String summary, final Runnable confirmAction) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Confirm action")
                        .setMessage(summary)
                        .setPositiveButton("Allow", (d, w) -> {
                            appendMessage("Allowed: " + summary, Sender.TOOL);
                            confirmAction.run();
                        })
                        .setNegativeButton("Deny", (d, w) -> {
                            appendMessage("Denied by user.", Sender.TOOL);
                            toolDepth++;
                            runTurn("TOOL_RESULT: user denied the action \"" + summary + "\". "
                                    + "Ask what they'd prefer instead.");
                        })
                        .show();
            }

            @Override public void onResult(String result) {
                if (result == null) result = "(no result)";

                // Intercept see_screen markers so the raw path never hits the chat
                if (result.equals("__SEE_SCREEN_PENDING__")) {
                    return;
                }
                if (result.startsWith("__SEE_SCREEN_PATH__")) {
                    handleSeeScreenResult(result);
                    return;
                }

                appendMessage(result, Sender.TOOL);
                toolDepth++;
                runTurn("TOOL_RESULT: " + result);
            }
        });
    }

    private void startAgentTask(final String goal) {
        appendMessage(goal, Sender.USER);
        setSending(false);

        currentAgent = new AgentSession(this, config, memoryStore, new AgentSession.Listener() {
            @Override public void onPlanning() {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendMessage("Planning...", Sender.TOOL);
                    }
                });
            }

            @Override public void onPlanReady(final Plan plan) {
                main.post(new Runnable() {
                    @Override public void run() {
                        renderAgentPlan(plan);
                    }
                });
            }

            @Override public void onStepStarted(final Plan plan, final int index) {
                main.post(new Runnable() {
                    @Override public void run() {
                        AgentRenderer.updateStep(MainActivity.this, currentPlanCard, plan, index);
                    }
                });
            }

            @Override public void onStepNote(final Plan plan, final int index, final String note) {
                main.post(new Runnable() {
                    @Override public void run() {
                        AgentRenderer.appendNote(MainActivity.this, currentPlanCard, index, note);
                        scroll.post(new Runnable() {
                            @Override public void run() {
                                scroll.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                });
            }

            @Override public void onStepFinished(final Plan plan, final int index,
                                                 boolean success, String result) {
                main.post(new Runnable() {
                    @Override public void run() {
                        AgentRenderer.updateStep(MainActivity.this, currentPlanCard, plan, index);
                        scroll.post(new Runnable() {
                            @Override public void run() {
                                scroll.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                });
            }

            @Override public void onStepRetrying(final Plan plan, final int index) {
                main.post(new Runnable() {
                    @Override public void run() {
                        if (currentPlanCard != null) {
                            AgentRenderer.appendNote(MainActivity.this,
                                    currentPlanCard, index, "↻ retrying...");
                        }
                        scroll.post(new Runnable() {
                            @Override public void run() {
                                scroll.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                });
            }

            @Override public void onReplanning(final Plan plan, final int afterIndex,
                                                final String reason) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendMessage("Replanning — " + reason, Sender.TOOL);
                    }
                });
            }

            @Override public void onPlanRevised(final Plan plan) {
                main.post(new Runnable() {
                    @Override public void run() {
                        // Rebuild the plan card to reflect new step count
                        if (currentPlanCard != null
                                && currentPlanCard.root.getParent() instanceof ViewGroup) {
                            ViewGroup parent = (ViewGroup) currentPlanCard.root.getParent();
                            int idx = parent.indexOfChild(currentPlanCard.root);
                            parent.removeView(currentPlanCard.root);
                            currentPlanCard = AgentRenderer.build(MainActivity.this, plan);
                            parent.addView(currentPlanCard.root, idx);
                        } else {
                            renderAgentPlan(plan);
                        }
                        appendMessage("Plan revised (Rev " + plan.revision + ")",
                                Sender.TOOL);
                    }
                });
            }

            @Override public void onReport(final String summary) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendMessage(summary, Sender.AI);
                        if (speakerOn) voice.speak(summary);
                    }
                });
            }

            @Override public void onError(final String reason) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendMessage("Agent error: " + reason, Sender.TOOL);
                    }
                });
            }

            @Override public void onFinished(final Plan plan) {
                main.post(new Runnable() {
                    @Override public void run() {
                        setSending(true);
                        currentAgent = null;
                    }
                });
            }
        });
        currentAgent.start(goal);
    }

    private void renderAgentPlan(Plan plan) {
        currentPlanCard = AgentRenderer.build(this, plan);
        messagesList.addView(currentPlanCard.root);
        currentPlanCard.root.setAlpha(0f);
        currentPlanCard.root.animate().alpha(1f).setDuration(220).start();
        scroll.post(new Runnable() {
            @Override public void run() {
                scroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
        updateEmptyState();
    }

    private void showModelPicker() {
        ModelPicker.show(this, config.model, new ModelPicker.OnPick() {
            @Override public void onPick(final String model) {
                config = new ApiConfig(config.endpoint, config.apiKey,
                        model, config.systemPrompt);
                keyStore.save(config);
                applySystemPrompt(false);
                Toast.makeText(MainActivity.this, "Model: " + model,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleAgentMode() {
        agentOn = !agentOn;
        if (agentToggleIcon != null) {
            agentToggleIcon.setColorFilter(agentOn ? Theme.PRIMARY : Theme.TEXT_SECONDARY);
        }
        Toast.makeText(this,
                agentOn ? "Agent mode ON — Ind AI will plan and execute tasks."
                        : "Agent mode OFF — normal chat.",
                Toast.LENGTH_SHORT).show();
    }

    private void showMessageActions(final String text, final Sender who) {
        final boolean isAi = who == Sender.AI;

        final java.util.List<String> actions = new java.util.ArrayList<>();
        if (isAi && lastUserMessage != null) actions.add("Regenerate");
        actions.add("Copy text");
        actions.add("Cancel");

        new android.app.AlertDialog.Builder(this)
                .setTitle(isAi ? "Ind AI message" : "Your message")
                .setItems(actions.toArray(new String[0]),
                        (d, which) -> {
                            String choice = actions.get(which);
                            if ("Copy text".equals(choice)) {
                                ClipboardManager cm = (ClipboardManager)
                                        getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(ClipData.newPlainText("Ind AI", text));
                                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                            } else if ("Regenerate".equals(choice)) {
                                regenerateLastResponse();
                            }
                        })
                .show();
    }

    private TextView actionRow(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Theme.TEXT_PRIMARY);
        t.setTextSize(Theme.T_BODY);
        int pH = Theme.dp(this, Theme.S4);
        int pV = Theme.dp(this, Theme.S4);
        t.setPadding(pH, pV, pH, pV);
        return t;
    }

    private void regenerateLastResponse() {
        if (lastUserMessage == null) return;
        JSONArray hist = ai.getHistory();
        // Drop trailing assistant message if present
        try {
            if (hist.length() > 0) {
                JSONObject last = hist.getJSONObject(hist.length() - 1);
                if ("assistant".equals(last.optString("role"))) {
                    hist.remove(hist.length() - 1);
                }
            }
        } catch (Exception ignored) {}
        ai.setHistory(hist);

        // Remove the last AI bubble + user bubble from the view
        int count = messagesList.getChildCount();
        if (count > 0) messagesList.removeViewAt(count - 1);
        if (messagesList.getChildCount() > 0) {
            messagesList.removeViewAt(messagesList.getChildCount() - 1);
        }

        chatStore.save(ai.getHistory());
        runTurn(lastUserMessage);
    }

    private void showSearch() {
        SearchDialog.show(this, ai.getHistory(), new SearchDialog.OnPick() {
            @Override public void onPick(int historyIndex) {
                Toast.makeText(MainActivity.this,
                        "Match found in history. Scroll to review.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleSeeScreenResult(String marker) {
        // Format: __SEE_SCREEN_PATH__/abs/path\n\n<tappable listing>
        String body = marker.substring("__SEE_SCREEN_PATH__".length());
        int split = body.indexOf("\n\n");
        String path = split >= 0 ? body.substring(0, split).trim() : body.trim();
        final String tappable = split >= 0 ? body.substring(split + 2).trim() : "";

        final java.io.File f = new java.io.File(path);
        if (!f.exists()) {
            appendMessage("Screenshot missing: " + path, Sender.TOOL);
            toolDepth++;
            runTurn("TOOL_RESULT: screenshot could not be saved.");
            return;
        }

        appendMessage("Captured screen. Analyzing...", Sender.TOOL);

        VisionClient.describeFile(this, config, f, null, new VisionClient.Callback() {
            @Override public void onSuccess(final String reply) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendMessage(reply, Sender.AI);
                        String combined = "SCREEN CONTENT:\n" + reply
                                + "\n\nTAPPABLE ELEMENTS:\n" + tappable;
                        toolDepth++;
                        runTurn("TOOL_RESULT: " + combined);
                    }
                });
            }

            @Override public void onError(final String error) {
                main.post(new Runnable() {
                    @Override public void run() {
                        appendMessage("Vision error: " + error, Sender.TOOL);
                        toolDepth++;
                        runTurn("TOOL_RESULT: screenshot captured but not described. "
                                + "Error: " + error);
                    }
                });
            }
        });
    }

    private void setSending(boolean enabled) {
        sendButton.setEnabled(enabled);
        sendButton.animate().alpha(enabled ? 1f : 0.5f).setDuration(150).start();
    }

    private void updateEmptyState() {
        boolean empty = messagesList.getChildCount() == 0;
        if (empty && emptyState.getVisibility() != View.VISIBLE) {
            emptyState.setVisibility(View.VISIBLE);
            emptyState.setAlpha(0f);
            emptyState.animate().alpha(1f).setDuration(220).start();
        } else if (!empty && emptyState.getVisibility() == View.VISIBLE) {
            emptyState.animate().alpha(0f).setDuration(150)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            emptyState.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    // ==================================================================
    //  Dialogs
    // ==================================================================

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear conversation?")
                .setMessage("Memory facts are kept.")
                .setPositiveButton("Clear", (d, w) -> {
                    applySystemPrompt(true);
                    chatStore.clear();
                    messagesList.removeAllViews();
                    updateEmptyState();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMemory() {
        // Memory now has its own screen in the bottom nav.
        // This method is retained as a fallback for the header icon.
        startActivity(new android.content.Intent(this, MemoryActivity.class));
    }

    private void showSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Theme.dp(this, Theme.S5), Theme.dp(this, Theme.S3),
                Theme.dp(this, Theme.S5), Theme.dp(this, Theme.S3));

        // ---- Primary provider row ----
        String primaryLabel = resolveLabel(
                config.primaryProviderName,
                config.endpoint,
                "Not set");
        addProviderRow(layout, "PRIMARY", primaryLabel, new Runnable() {
            @Override public void run() {
                ProviderPicker.show(MainActivity.this, config.primaryProviderName,
                        config.apiKey, new ProviderPicker.OnPick() {
                    @Override public void onPick(Providers.Provider provider, String apiKey) {
                        config = new ApiConfig(
                                provider.endpoint,
                                apiKey,
                                provider.defaultModel,
                                config.systemPrompt,
                                config.visionModel,
                                config.fallbackEndpoint, config.fallbackApiKey, config.fallbackModel,
                                config.visionEndpoint, config.visionApiKey,
                                provider.name,
                                config.fallbackProviderName,
                                config.visionProviderName);
                        keyStore.save(config);
                        applySystemPrompt(true);
                        Toast.makeText(MainActivity.this,
                                "Primary: " + provider.name, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // ---- Fallback provider row ----
        String fbLabel = config.hasFallback()
                ? resolveLabel(config.fallbackProviderName, config.fallbackEndpoint, "Set")
                : "None";
        addProviderRow(layout, "FALLBACK  (used on 429 / 5xx)", fbLabel, new Runnable() {
            @Override public void run() {
                ProviderPicker.show(MainActivity.this, config.fallbackProviderName,
                        config.fallbackApiKey, new ProviderPicker.OnPick() {
                    @Override public void onPick(Providers.Provider provider, String apiKey) {
                        config = new ApiConfig(
                                config.endpoint, config.apiKey, config.model,
                                config.systemPrompt, config.visionModel,
                                provider.endpoint, apiKey, provider.defaultModel,
                                config.visionEndpoint, config.visionApiKey,
                                config.primaryProviderName,
                                provider.name,
                                config.visionProviderName);
                        keyStore.save(config);
                        Toast.makeText(MainActivity.this,
                                "Fallback: " + provider.name, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        if (config.hasFallback()) {
            TextView clearFb = new TextView(this);
            clearFb.setText("    Clear fallback");
            clearFb.setTextColor(Theme.ERROR);
            clearFb.setTextSize(Theme.T_CAPTION - 1f);
            clearFb.setPadding(0, 0, 0, Theme.dp(this, Theme.S3));
            clearFb.setOnClickListener(v -> {
                config = new ApiConfig(
                        config.endpoint, config.apiKey, config.model,
                        config.systemPrompt, config.visionModel,
                        "", "", "",
                        config.visionEndpoint, config.visionApiKey,
                        config.primaryProviderName, "", config.visionProviderName);
                keyStore.save(config);
                Toast.makeText(this, "Fallback cleared", Toast.LENGTH_SHORT).show();
            });
            layout.addView(clearFb);
        }

        // ---- Vision provider row ----
        String vLabel = config.hasVisionProvider()
                ? resolveLabel(config.visionProviderName, config.visionEndpoint, "Set")
                : "Uses primary";
        addProviderRow(layout, "VISION  (for images)", vLabel, new Runnable() {
            @Override public void run() {
                ProviderPicker.show(MainActivity.this, config.visionProviderName,
                        config.visionApiKey, new ProviderPicker.OnPick() {
                    @Override public void onPick(Providers.Provider provider, String apiKey) {
                        config = new ApiConfig(
                                config.endpoint, config.apiKey, config.model,
                                config.systemPrompt,
                                provider.defaultModel,
                                config.fallbackEndpoint, config.fallbackApiKey, config.fallbackModel,
                                provider.endpoint, apiKey,
                                config.primaryProviderName,
                                config.fallbackProviderName,
                                provider.name);
                        keyStore.save(config);
                        Toast.makeText(MainActivity.this,
                                "Vision: " + provider.name, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        if (config.hasVisionProvider()) {
            TextView clearV = new TextView(this);
            clearV.setText("    Clear vision provider");
            clearV.setTextColor(Theme.ERROR);
            clearV.setTextSize(Theme.T_CAPTION - 1f);
            clearV.setPadding(0, 0, 0, Theme.dp(this, Theme.S3));
            clearV.setOnClickListener(v -> {
                config = new ApiConfig(
                        config.endpoint, config.apiKey, config.model,
                        config.systemPrompt, "",
                        config.fallbackEndpoint, config.fallbackApiKey, config.fallbackModel,
                        "", "",
                        config.primaryProviderName,
                        config.fallbackProviderName, "");
                keyStore.save(config);
                Toast.makeText(this, "Vision cleared", Toast.LENGTH_SHORT).show();
            });
            layout.addView(clearV);
        }

        // ---- Personality section ----
        TextView personalityLabel = new TextView(this);
        personalityLabel.setText("PERSONALITY");
        personalityLabel.setTextColor(Theme.TEXT_SECONDARY);
        personalityLabel.setTextSize(Theme.T_CAPTION - 1f);
        personalityLabel.setPadding(0, Theme.dp(this, Theme.S4), 0, Theme.dp(this, Theme.S1));
        layout.addView(personalityLabel);

        final EditText userNameField = field(layout, "Your name",
                identityStore.getUserName());
        final EditText userRoleField = field(layout, "Your role / context (optional)",
                identityStore.getUserRole());

        addChoiceRow(layout, "Tone", identityStore.getTone(),
                new String[]{ IdentityStore.TONE_CASUAL, IdentityStore.TONE_FORMAL,
                        IdentityStore.TONE_CONCISE, IdentityStore.TONE_DETAILED },
                value -> identityStore.setTone(value));

        addChoiceRow(layout, "Language", identityStore.getLanguage(),
                new String[]{ IdentityStore.LANG_AUTO, IdentityStore.LANG_ENGLISH,
                        IdentityStore.LANG_BENGALI, IdentityStore.LANG_HINDI },
                value -> identityStore.setLanguage(value));

        addChoiceRow(layout, "Greeting", identityStore.getGreeting(),
                new String[]{ IdentityStore.GREETING_WARM, IdentityStore.GREETING_SIMPLE,
                        IdentityStore.GREETING_PROFESSIONAL },
                value -> identityStore.setGreeting(value));

        // ---- System prompt section ----
        TextView promptLabel = new TextView(this);
        promptLabel.setText("SYSTEM PROMPT");
        promptLabel.setTextColor(Theme.TEXT_SECONDARY);
        promptLabel.setTextSize(Theme.T_CAPTION - 1f);
        promptLabel.setPadding(0, Theme.dp(this, Theme.S4), 0, Theme.dp(this, Theme.S1));
        layout.addView(promptLabel);

        final EditText promptField = field(layout, "System prompt", config.systemPrompt);
        promptField.setSingleLine(false);
        promptField.setMinLines(4);

        TextView resetPrompt = new TextView(this);
        resetPrompt.setText("Reset to Ind AI master prompt");
        resetPrompt.setTextColor(Theme.PRIMARY);
        resetPrompt.setTextSize(Theme.T_CAPTION);
        resetPrompt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        resetPrompt.setPadding(0, Theme.dp(this, Theme.S3), 0, Theme.dp(this, Theme.S4));
        resetPrompt.setOnClickListener(v -> {
            String fresh = keyStore.loadDefaultPrompt();
            promptField.setText(fresh);
            Toast.makeText(MainActivity.this,
                    "Prompt reset. Tap Save to apply.", Toast.LENGTH_SHORT).show();
        });
        layout.addView(resetPrompt);

        ScrollView sw = new ScrollView(this);
        sw.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(sw)
                .setPositiveButton("Save", (d, w) -> {
                    identityStore.setUserName(userNameField.getText().toString().trim());
                    identityStore.setUserRole(userRoleField.getText().toString().trim());
                    config = new ApiConfig(
                            config.endpoint, config.apiKey, config.model,
                            promptField.getText().toString().trim(),
                            config.visionModel,
                            config.fallbackEndpoint, config.fallbackApiKey, config.fallbackModel,
                            config.visionEndpoint, config.visionApiKey,
                            config.primaryProviderName,
                            config.fallbackProviderName,
                            config.visionProviderName);
                    keyStore.save(config);
                    applySystemPrompt(false);
                    Toast.makeText(MainActivity.this, "Saved.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Export", (d, w) ->
                        ChatExporter.share(MainActivity.this, ai.getHistory()))
                .setNegativeButton("Close", null)
                .show();
    }

    private void addProviderRow(LinearLayout parent, String label,
                                String value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int pH = Theme.dp(this, Theme.S4);
        int pV = Theme.dp(this, Theme.S3);
        row.setPadding(pH, pV, pH, pV);
        row.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Theme.dp(this, Theme.S2);
        row.setLayoutParams(rlp);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Theme.TEXT_SECONDARY);
        lbl.setTextSize(Theme.T_CAPTION - 1f);
        row.addView(lbl);

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(Theme.PRIMARY);
        val.setTextSize(Theme.T_BODY);
        val.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = Theme.dp(this, Theme.S1);
        row.addView(val, vlp);

        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            onClick.run();
        });

        parent.addView(row);
    }

    public interface ChoiceConsumer {
        void accept(String value);
    }

    private void addChoiceRow(LinearLayout parent, String label, String current,
                              final String[] options, final ChoiceConsumer onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pH = Theme.dp(this, Theme.S4);
        int pV = Theme.dp(this, Theme.S3);
        row.setPadding(pH, pV, pH, pV);
        row.setBackground(Drawables.outlined(this,
                Theme.SURFACE, Theme.SURFACE_STROKE, Theme.R_MD, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Theme.dp(this, Theme.S2);
        row.setLayoutParams(rlp);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Theme.TEXT_SECONDARY);
        lbl.setTextSize(Theme.T_CAPTION);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(lbl);

        final TextView value = new TextView(this);
        value.setText(current);
        value.setTextColor(Theme.PRIMARY);
        value.setTextSize(Theme.T_BODY);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(value);

        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setItems(options, (d, which) -> {
                        value.setText(options[which]);
                        onPick.accept(options[which]);
                        Toast.makeText(MainActivity.this,
                                label + ": " + options[which], Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });

        parent.addView(row);
    }

    private String resolveLabel(String providerName, String endpoint, String fallback) {
        if (providerName != null && !providerName.isEmpty()) return providerName;
        Providers.Provider p = Providers.findByEndpoint(endpoint);
        if (p != null) return p.name;
        if (endpoint != null && !endpoint.isEmpty()) return "(custom)";
        return fallback;
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Theme.TEXT_TERTIARY);
        e.setTextColor(Theme.TEXT_PRIMARY);
        e.setText(value);
        e.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, Theme.S2);
        parent.addView(e, lp);
        return e;
    }

    @Override
    protected void onDestroy() {
        if (voice != null) voice.shutdown();
        super.onDestroy();
    }
}
