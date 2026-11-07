package app.keymold;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.text.InputType;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebViewClient;
import org.json.JSONObject;

public class KeymoldIME extends InputMethodService {
    private WebView web;
    private ClipboardManager cm;
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> pushCfg();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::onClip;

    @Override public void onCreate() {
        super.onCreate();
        Crash.install(this);
        cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.addPrimaryClipChangedListener(clipListener);
        prefs = getSharedPreferences("km", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override public void onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener);
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        stopVoice();
        super.onDestroy();
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView makeWeb() {
        if (web != null) { try { web.destroy(); } catch (Exception ignored) { } }
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);
        web.getSettings().setTextZoom(100);
        web.getSettings().setSupportZoom(false);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setLayoutParams(new ViewGroup.LayoutParams(-1, px(312)));
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) { pushCfg(); }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                post(() -> {
                    try { v.destroy(); } catch (Exception ignored) { }
                    long n = System.currentTimeMillis();
                    quick = (n - lastDeath < 20000) ? quick + 1 : 1;
                    lastDeath = n;
                    if (quick > 3) {
                        android.widget.TextView tv = new android.widget.TextView(KeymoldIME.this);
                        tv.setText("Kbrd paused after repeated errors. Switch keyboards with the keyboard icon, then reopen Kbrd.");
                        tv.setTextColor(0xFFFFFFFF);
                        tv.setBackgroundColor(0xFF000000);
                        tv.setPadding(40, 40, 40, 40);
                        setInputView(tv);
                        return;
                    }
                    setInputView(makeWeb());
                });
                return true;
            }
        });
        web.loadUrl("file:///android_asset/keyboard.html");
        return web;
    }

    @Override public View onCreateInputView() { return makeWeb(); }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (web != null) seedFor(info);
        pushCfg();
    }

    private void pushCfg() {
        String c = prefs.getString("cfg", null);
        if (web != null && c != null)
            web.evaluateJavascript("applyCfg(JSON.parse(" + JSONObject.quote(c) + "))", null);
    }

    private int px(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }

    private void key(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private void onClip() {
        try {
            ClipData d = cm.getPrimaryClip();
            if (web == null || d == null || d.getItemCount() == 0) return;
            CharSequence t = d.getItemAt(0).coerceToText(this);
            if (t != null && t.length() > 0)
                web.evaluateJavascript("addClip({t:" + JSONObject.quote(t.toString()) + "})", null);
        } catch (Exception ignored) { }
    }

    private SpeechRecognizer sr;
    private boolean listening;
    private int empties;
    private long lastDeath;
    private int quick;
    private final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Runnable reseed = () -> io(this::reseedNow);
    private String voiceLang = "en-IN";

    private void voiceEvt(String t, String s) {
        js("voiceEvt('" + t + "'," + JSONObject.quote(s == null ? "" : s) + ")");
    }

    private static String first(Bundle b) {
        ArrayList<String> l = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    private void again(long ms) {
        ui.postDelayed(() -> {
            try { if (listening && sr != null) listenOnce(); } catch (Exception e) { listening = false; }
        }, ms);
    }

    private void listenOnce() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        sr.startListening(i);
    }

    private void stopVoice() {
        listening = false;
        if (sr != null) {
            try { sr.cancel(); sr.destroy(); } catch (Exception ignored) { }
            sr = null;
        }
    }

    private void startVoice(String lang) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceEvt("error", "Allow the microphone in the Keymold app to use voice typing.");
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("perm", true));
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceEvt("error", "No speech service found on this phone.");
            return;
        }
        stopVoice();
        listening = true;
        empties = 0;
        voiceLang = lang == null ? "en-IN" : lang;
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int t, Bundle b) { }
            @Override public void onPartialResults(Bundle b) {
                String t = first(b);
                if (t != null) voiceEvt("partial", t);
            }
            @Override public void onResults(Bundle b) {
                String t = first(b);
                if (t != null) { empties = 0; voiceEvt("final", t); }
                again(150);
            }
            @Override public void onError(int e) {
                if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (++empties > 4) {
                        listening = false;
                        voiceEvt("error", "Didn't catch that. Tap the mic to try again.");
                        return;
                    }
                    again(150);
                    return;
                }
                if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) { again(500); return; }
                listening = false;
                String m = e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ? "Allow the microphone in the Keymold app to use voice typing."
                        : (e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                                || e == SpeechRecognizer.ERROR_SERVER)
                        ? "Your phone's speech service needs a connection, or an offline language pack."
                        : "Voice typing stopped (error " + e + ").";
                voiceEvt("error", m);
            }
        });
        listenOnce();
    }

    @Override public void onWindowHidden() {
        super.onWindowHidden();
        stopVoice();
        js("try{stopR();stopV()}catch(e){}");
    }

    private boolean blocked(EditorInfo ei) {
        if (ei == null) return false;
        int cls = ei.inputType & InputType.TYPE_MASK_CLASS, var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        boolean pw = (cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return pw || (ei.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    }

    private void srcCb(JSONObject o) {
        final String q = JSONObject.quote(o.toString());
        post(() -> js("aiSrcCb(" + q + ")"));
    }

    private void post(Runnable r) {
        ui.post(() -> {
            try { r.run(); } catch (Throwable t) { Crash.note(this, t); }
        });
    }

    private void io(Runnable r) {
        IO.execute(() -> {
            try { r.run(); } catch (Throwable t) { Crash.note(this, t); }
        });
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        js("try{autoFit(false)}catch(e){}");
    }

    private void js(String code) {
        try { if (web != null) web.evaluateJavascript(code, null); } catch (Exception ignored) { }
    }

    private String ctxBefore() {
        try {
            InputConnection ic = getCurrentInputConnection();
            CharSequence b = ic == null ? null : ic.getTextBeforeCursor(40, 0);
            return b == null ? "" : b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void seedFor(EditorInfo ei) {
        int cls = ei == null ? 0 : ei.inputType & InputType.TYPE_MASK_CLASS;
        final boolean num = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE;
        final boolean caps = cls == InputType.TYPE_CLASS_TEXT && (ei.inputType & (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) != 0;
        io(() -> {
            final String c = JSONObject.quote(ctxBefore());
            post(() -> js("try{stopR();stopV()}catch(e){}seedCtx(" + c + "," + caps + ",'" + (num ? "num" : "abc") + "')"));
        });
    }

    private void reseedNow() {
        final String c = JSONObject.quote(ctxBefore());
        post(() -> js("seedCtx(" + c + ")"));
    }

    @Override public void onUpdateSelection(int os, int oe, int ns, int ne, int cs, int ce) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce);
        ui.removeCallbacks(reseed);
        ui.postDelayed(reseed, 150);
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        js("try{stopR();stopV()}catch(e){}");
    }

    class Bridge {
        @JavascriptInterface public void edit(int del, String ins, int h) {
            Haptics.buzz(KeymoldIME.this, h);
            post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                if (del <= 0) {
                    if (ins != null && ins.length() > 0) ic.commitText(ins, 1);
                    return;
                }
                ic.beginBatchEdit();
                ic.deleteSurroundingText(del, 0);
                if (ins != null && ins.length() > 0) ic.commitText(ins, 1);
                ic.endBatchEdit();
            });
        }

        @JavascriptInterface public void voiceStart(String lang) { post(() -> startVoice(lang)); }

        @JavascriptInterface public void voiceStop() { post(() -> stopVoice()); }

        @JavascriptInterface public String device() {
            try {
                DisplayMetrics m = getResources().getDisplayMetrics();
                return new JSONObject().put("w", Math.round(m.widthPixels / m.density))
                        .put("h", Math.round(m.heightPixels / m.density)).toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface public String aiState() { return Ai.state(KeymoldIME.this); }

        @JavascriptInterface public void aiConsent() { Ai.consent(KeymoldIME.this); }

        @JavascriptInterface public void aiSrc() {
            io(() -> {
                JSONObject o = new JSONObject();
                try {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic == null) { srcCb(o.put("ok", false).put("err", "No text field is active.")); return; }
                    if (blocked(getCurrentInputEditorInfo())) {
                        srcCb(o.put("ok", false).put("err", "AI is turned off in password and private fields."));
                        return;
                    }
                    CharSequence sel = ic.getSelectedText(0);
                    boolean hasSel = sel != null && sel.length() > 0;
                    CharSequence bf = hasSel ? null : ic.getTextBeforeCursor(1500, 0);
                    String text = hasSel ? sel.toString() : (bf == null ? "" : bf.toString());
                    String clip = "";
                    try {
                        ClipData d = cm.getPrimaryClip();
                        if (d != null && d.getItemCount() > 0) {
                            CharSequence t = d.getItemAt(0).coerceToText(KeymoldIME.this);
                            if (t != null) clip = t.toString();
                        }
                    } catch (Exception ignored) { }
                    if (clip.length() > 1500) clip = clip.substring(0, 1500);
                    srcCb(o.put("ok", true).put("text", text).put("len", text.length()).put("hasSel", hasSel).put("clip", clip));
                } catch (Exception e) {
                    try { srcCb(new JSONObject().put("ok", false).put("err", "Couldn't read the text field.")); } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface public void aiRun(int id, String system, String user) {
            Ai.chat(KeymoldIME.this, system, user, (text, err) -> post(() -> {
                if (web == null) return;
                web.evaluateJavascript("aiEvt(" + id + "," + (err == null) + "," + JSONObject.quote(err == null ? text : err) + ")", null);
            }));
        }

        @JavascriptInterface public void aiApply(String text, int del) {
            post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                ic.beginBatchEdit();
                if (del > 0) ic.deleteSurroundingText(del, 0);
                ic.commitText(text, 1);
                ic.endBatchEdit();
            });
        }

        @JavascriptInterface public void aiCopy(String text) {
            post(() -> cm.setPrimaryClip(ClipData.newPlainText("Kbrd", text)));
        }

        @JavascriptInterface public void openApp() {
            post(() -> startActivity(new Intent(KeymoldIME.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }

        @JavascriptInterface public void back(int h) {
            Haptics.buzz(KeymoldIME.this, h);
            post(() -> key(KeyEvent.KEYCODE_DEL));
        }

        @JavascriptInterface public void enter() {
            post(() -> {
                InputConnection ic = getCurrentInputConnection();
                EditorInfo ei = getCurrentInputEditorInfo();
                if (ic == null) return;
                int a = ei == null ? EditorInfo.IME_ACTION_NONE : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
                boolean noAction = ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                        || a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED;
                if (noAction) key(KeyEvent.KEYCODE_ENTER); else ic.performEditorAction(a);
            });
        }

        @JavascriptInterface public void move(int steps) {
            post(() -> {
                int code = steps < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                for (int i = 0; i < Math.abs(steps); i++) key(code);
            });
        }

        @JavascriptInterface public void height(int cssPx) {
            post(() -> {
                if (web == null) return;
                ViewGroup.LayoutParams p = web.getLayoutParams();
                if (p == null) return;
                p.height = px(cssPx);
                web.setLayoutParams(p);
                web.requestLayout();
            });
        }

        @JavascriptInterface public void tick(int h) { Haptics.buzz(KeymoldIME.this, h); }
    }
}
