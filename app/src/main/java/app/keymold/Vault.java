package app.keymold;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the user's API key encrypted with an Android Keystore AES key. */
final class Vault {
    private static final String ALIAS = "kbrd_ai_key";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("km_secure", Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            g.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    static void put(Context c, String plain) {
        try {
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = ci.getIV();
            byte[] ct = ci.doFinal(plain.getBytes("UTF-8"));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            prefs(c).edit().putString("k", Base64.encodeToString(all, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) { }
    }

    static String get(Context c) {
        try {
            String b = prefs(c).getString("k", null);
            if (b == null) return null;
            byte[] all = Base64.decode(b, Base64.NO_WRAP);
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(ci.doFinal(all, 12, all.length - 12), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    static void clear(Context c) {
        prefs(c).edit().remove("k").apply();
    }
}
