package app.keymold;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);

        LinearLayout row = new LinearLayout(this);
        Button enable = new Button(this);
        enable.setText("1. Enable keyboard");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        Button pick = new Button(this);
        pick.setText("2. Switch to Keymold");
        pick.setOnClickListener(v ->
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        row.addView(enable, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(pick, new LinearLayout.LayoutParams(0, -2, 1f));

        WebView w = new WebView(this);
        w.setBackgroundColor(Color.BLACK);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.loadUrl("file:///android_asset/keyboard.html");

        root.addView(row);
        root.addView(w, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }
}
