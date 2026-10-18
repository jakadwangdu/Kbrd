package app.keymold;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import org.json.JSONObject;

public class KeymoldIME extends InputMethodService {
    private WebView web;
    private ClipboardManager cm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::onClip;

    @Override public void onCreate() {
        super.onCreate();
        cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.addPrimaryClipChangedListener(clipListener);
    }

    @Override public void onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override public View onCreateInputView() {
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setLayoutParams(new ViewGroup.LayoutParams(-1, px(312)));
        web.loadUrl("file:///android_asset/keyboard.html");
        return web;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (web != null) web.evaluateJavascript("txt='';lastTxt='';out()", null);
    }

    private int px(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }

    private void key(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private void onClip() {
        ClipData d = cm.getPrimaryClip();
        if (web == null || d == null || d.getItemCount() == 0) return;
        CharSequence t = d.getItemAt(0).coerceToText(this);
        if (t != null && t.length() > 0)
            web.evaluateJavascript("addClip({t:" + JSONObject.quote(t.toString()) + "})", null);
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

        @JavascriptInterface public void height(int cssPx) {
            ui.post(() -> {
                if (web == null) return;
                ViewGroup.LayoutParams p = web.getLayoutParams();
                p.height = px(cssPx);
                web.setLayoutParams(p);
            });
        }

        @JavascriptInterface public void tick() {
            ui.post(() -> { if (web != null) web.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); });
        }
    }
}
