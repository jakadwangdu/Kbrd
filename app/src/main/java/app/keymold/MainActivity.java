package app.keymold;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private TextView status;
    private WebView web;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private Button btn(String t, boolean fill, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        if (fill) { g.setColor(0xFFFFFFFF); b.setTextColor(0xFF000000); }
        else { g.setColor(0xFF111111); g.setStroke(dp(1), 0x33FFFFFF); b.setTextColor(0xFFF5F5F5); }
        b.setBackground(g);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout pair(Button a, Button b) {
        LinearLayout r = new LinearLayout(this);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(46), 1f);
        l.setMargins(0, 0, dp(8), 0);
        r.addView(a, l);
        r.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, dp(8), 0, 0);
        r.setLayoutParams(rp);
        return r;
    }

    private void cb(String type, String payload) {
        runOnUiThread(() -> web.evaluateJavascript("aiCb('" + type + "'," + JSONObject.quote(payload) + ")", null));
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(12), dp(16), 0);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        head.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text("Kbrd", 26, 0xFFFFFFFF, true));
        col.addView(text("Minimal keyboard. AI you control.", 12, 0xFF9A9A9A, false));
        head.addView(col);
        root.addView(head);

        status = text("", 13, 0xFFFFFFFF, false);
        status.setPadding(0, dp(10), 0, dp(2));
        root.addView(status);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void buzz(int h) { Haptics.buzz(MainActivity.this, h); }
            @JavascriptInterface public void save(String json) {
                getSharedPreferences("km", MODE_PRIVATE).edit().putString("cfg", json).apply();
            }
            @JavascriptInterface public String aiConfig() { return Ai.config(MainActivity.this); }
            @JavascriptInterface public void aiSave(String json) { Ai.save(MainActivity.this, json); }
            @JavascriptInterface public void aiClear() { Vault.clear(MainActivity.this); }
            @JavascriptInterface public void aiTest() {
                Ai.chat(MainActivity.this, "Reply with the single word OK.", "ping", (t, e) ->
                        cb("test", e == null ? "Connected. The model replied: " + (t.length() > 40 ? t.substring(0, 40) : t) : e));
            }
            @JavascriptInterface public void aiModels(String base, String key) {
                Ai.models(MainActivity.this, base, key, (t, e) -> cb(e == null ? "models" : "error", e == null ? t : e));
            }
        }, "AppBridge");
        web.loadUrl("file:///android_asset/keyboard.html");

        root.addView(pair(
                btn("1. Enable keyboard", true, v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))),
                btn("2. Switch to Kbrd", false, v ->
                        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker())));
        root.addView(pair(
                btn("3. Allow microphone", false, v -> askMic()),
                btn("4. Set up AI", false, v -> web.evaluateJavascript("openAi()", null))));

        EditText test = new EditText(this);
        test.setHint("Tap here to try Kbrd");
        test.setHintTextColor(0xFF8A8A8A);
        test.setTextColor(0xFFF5F5F5);
        test.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable tg = new GradientDrawable();
        tg.setCornerRadius(dp(14));
        tg.setColor(0xFF111111);
        test.setBackground(tg);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.setMargins(0, dp(10), 0, dp(6));
        root.addView(test, tp);

        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        if (getIntent().getBooleanExtra("perm", false)) askMic();
    }

    private void askMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
        else if (getIntent().getBooleanExtra("perm", false)) finish();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (getIntent().getBooleanExtra("perm", false)) finish();
    }

    @Override protected void onResume() {
        super.onResume();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo i : imm.getEnabledInputMethodList())
            if (i.getPackageName().equals(getPackageName())) enabled = true;
        String def = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean active = def != null && def.startsWith(getPackageName() + "/");
        status.setText(!enabled ? "Step 1: enable Kbrd in keyboard settings"
                : !active ? "Step 2: switch to Kbrd"
                : "Kbrd is active \u2713  Try it below");
    }
}
