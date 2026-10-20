package app.keymold;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class Haptics {
    static void buzz(Context c, int h) {
        if (h <= 0) return;
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        int ms = 6 + h / 8;
        int amp = v.hasAmplitudeControl() ? Math.min(255, 30 + h * 2) : VibrationEffect.DEFAULT_AMPLITUDE;
        v.vibrate(VibrationEffect.createOneShot(ms, amp));
    }
}
