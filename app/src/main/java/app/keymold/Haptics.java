package app.keymold;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class Haptics {
    private static Vibrator vib;
    private static boolean amp;

    static void buzz(Context c, int h) {
        if (h <= 0) return;
        if (vib == null) {
            vib = (Vibrator) c.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) amp = vib.hasAmplitudeControl();
        }
        if (vib == null) return;
        int ms = 6 + h / 8;
        int a = amp ? Math.min(255, 30 + h * 2) : VibrationEffect.DEFAULT_AMPLITUDE;
        vib.vibrate(VibrationEffect.createOneShot(ms, a));
    }
}
