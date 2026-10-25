package app.keymold;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private Button btn(String t, boolean fill, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        if (fill) { g.setColor(0xFFF0A93B); b.setTextColor(0xFF1A1204); }
        else { g.setColor(0xFF161616); g.setStroke(dp(1), 0x33FFFFFF); b.setTextColor(0xFFF5F5F5); }
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

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(12), dp(16), 0);

        root.addView(text("Keymold", 28, 0xFFF5F5F5, true));
        status = text("", 14, 0xFFF0A93B, false);
        status.setPadding(0, dp(4), 0, dp(12));
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        Button enable = btn("1. Enable", true, v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        Button pick = btn("2. Switch to Keymold", false, v ->
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(0, 0, dp(8), 0);
        row.addView(enable, lp);
        row.addView(pick, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(row);
        Button mic = btn("3. Allow microphone for voice typing", false, v -> askMic());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(48));
        mp.setMargins(0, dp(8), 0, 0);
        root.addView(mic, mp);
        if (getIntent().getBooleanExtra("perm", false)) askMic();

        EditText test = new EditText(this);
        test.setHint("Tap here and try the keyboard");
        test.setHintTextColor(0xFF8A8A8A);
        test.setTextColor(0xFFF5F5F5);
        test.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable tg = new GradientDrawable();
        tg.setCornerRadius(dp(14));
        tg.setColor(0xFF161616);
        test.setBackground(tg);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.setMargins(0, dp(12), 0, dp(8));
        root.addView(test, tp);

        TextView hint = text("Theme, key size and layout settings are below.", 12, 0xFF8A8A8A, false);
        hint.setPadding(dp(2), 0, 0, dp(6));
        root.addView(hint);

        WebView w = new WebView(this);
        w.setBackgroundColor(Color.BLACK);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.addJavascriptInterface(new Object() {
            @JavascriptInterface public void buzz(int h) { Haptics.buzz(MainActivity.this, h); }
            @JavascriptInterface public void save(String json) {
                getSharedPreferences("km", MODE_PRIVATE).edit().putString("cfg", json).apply();
            }
        }, "AppBridge");
        w.loadUrl("file:///android_asset/keyboard.html");
        root.addView(w, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
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
        status.setText(!enabled ? "Step 1: enable Keymold in keyboard settings"
                : !active ? "Step 2: switch to Keymold"
                : "Keymold is active \u2713 try it below");
    }
}
