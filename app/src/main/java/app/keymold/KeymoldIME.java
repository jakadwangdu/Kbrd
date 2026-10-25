package app.keymold;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
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
    @Override public View onCreateInputView() {
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
        });
        web.loadUrl("file:///android_asset/keyboard.html");
        return web;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (web != null) web.evaluateJavascript("txt='';lastTxt='';cur=-1;if(mode!=='abc'){stopV();mode='abc';body()}out()", null);
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
    private String voiceLang = "en-IN";

    private void voiceEvt(String t, String s) {
        if (web != null)
            web.evaluateJavascript("voiceEvt('" + t + "'," + JSONObject.quote(s == null ? "" : s) + ")", null);
    }

    private static String first(Bundle b) {
        ArrayList<String> l = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    private void again(long ms) {
        ui.postDelayed(() -> { if (listening && sr != null) listenOnce(); }, ms);
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
                if (t != null) voiceEvt("final", t);
                again(150);
            }
            @Override public void onError(int e) {
                if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { again(150); return; }
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
    }

    class Bridge {
        @JavascriptInterface public void edit(int del, String ins) {
            ui.post(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                ic.beginBatchEdit();
                if (del > 0) ic.deleteSurroundingText(del, 0);
                if (ins != null && ins.length() > 0) ic.commitText(ins, 1);
                ic.endBatchEdit();
            });
        }

        @JavascriptInterface public void voiceStart(String lang) { ui.post(() -> startVoice(lang)); }

        @JavascriptInterface public void voiceStop() { ui.post(() -> stopVoice()); }

        @JavascriptInterface public void back() { ui.post(() -> key(KeyEvent.KEYCODE_DEL)); }

        @JavascriptInterface public void enter() {
            ui.post(() -> {
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
            ui.post(() -> {
                int code = steps < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                for (int i = 0; i < Math.abs(steps); i++) key(code);
            });
        }

        @JavascriptInterface public void height(int cssPx) {
            ui.post(() -> {
                if (web == null) return;
                ViewGroup.LayoutParams p = web.getLayoutParams();
                p.height = px(cssPx);
                web.setLayoutParams(p);
                web.requestLayout();
            });
        }

        @JavascriptInterface public void tick(int h) { Haptics.buzz(KeymoldIME.this, h); }
    }
}
