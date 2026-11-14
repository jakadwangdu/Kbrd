package app.keymold;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/** Saves the last crash or handled error so it can be shared from the app screen. */
final class Crash {
    private static SharedPreferences p(Context c) { return c.getApplicationContext().getSharedPreferences("km", Context.MODE_PRIVATE); }

    static void install(Context c) {
        final Context a = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            save(a, "Crash on thread " + t.getName(), e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static void note(Context c, Throwable e) { save(c.getApplicationContext(), "Handled error", e); }

    private static void save(Context a, String kind, Throwable e) {
        try {
            StringWriter w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            String s = new Date() + "\n" + kind + "\n" + w;
            p(a).edit().putString("last_crash", s.length() > 3000 ? s.substring(0, 3000) : s).commit();
        } catch (Throwable ignored) { }
    }

    static boolean has(Context c) { return p(c).contains("last_crash"); }

    static String take(Context c) {
        String s = p(c).getString("last_crash", null);
        p(c).edit().remove("last_crash").apply();
        return s;
    }
}
