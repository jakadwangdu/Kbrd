package app.keymold;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Vibrates on a background thread so typing never waits on the vibration service. */
final class Haptics {
    private static Vibrator vib;
    private static boolean amp;
    private static Handler h;

    static synchronized void buzz(Context c, int level) {
        if (level <= 0) return;
        try {
            if (h == null) {
                HandlerThread t = new HandlerThread("kbrd-haptics");
                t.start();
                h = new Handler(t.getLooper());
            }
            final Context a = c.getApplicationContext();
            h.removeCallbacksAndMessages(null);
            h.post(() -> {
                try {
                    if (vib == null) {
                        vib = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
                        if (vib != null) amp = vib.hasAmplitudeControl();
                    }
                    if (vib == null) return;
                    int ms = 6 + level / 8;
                    int v = amp ? Math.min(255, 30 + level * 2) : VibrationEffect.DEFAULT_AMPLITUDE;
                    vib.vibrate(VibrationEffect.createOneShot(ms, v));
                } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }
}
