package app.keymold;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Talks to any OpenAI-compatible chat API using the user's own key. */
final class Ai {
    interface Cb { void done(String text, String err); }

    private static final ExecutorService EX = Executors.newFixedThreadPool(2);

    static final String FREE_BASE = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1", FREE_MODEL = "gpt-oss-120b";

    private static class Err extends Exception { Err(String m) { super(m); } }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("km", Context.MODE_PRIVATE); }

    static String config(Context c) {
        SharedPreferences p = prefs(c);
        try {
            String pv = p.getString("ai_provider", "free");
            boolean fr = pv.equals("free");
            return new JSONObject().put("provider", pv)
                    .put("base", p.getString("ai_base", fr ? FREE_BASE : ""))
                    .put("model", p.getString("ai_model", fr ? FREE_MODEL : ""))
                    .put("hasKey", Vault.has(c)).toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static boolean ready(Context c) {
        SharedPreferences p = prefs(c);
        return state(c).equals("ready");
    }

    static String state(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getString("ai_provider", "free").equals("free")) return p.getBoolean("free_ok", false) ? "ready" : "consent";
        return Vault.has(c) && !p.getString("ai_base", "").isEmpty() && !p.getString("ai_model", "").isEmpty() ? "ready" : "nokey";
    }

    static void consent(Context c) { prefs(c).edit().putBoolean("free_ok", true).apply(); }

    static void save(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            String base = o.optString("base").trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            prefs(c).edit().putString("ai_provider", o.optString("provider", "custom"))
                    .putString("ai_base", base).putString("ai_model", o.optString("model").trim()).apply();
            if (o.optString("provider").equals("free")) prefs(c).edit().putBoolean("free_ok", true).apply();
            String key = o.optString("key").trim();
            if (!key.isEmpty()) Vault.put(c, key);
        } catch (Exception ignored) { }
    }

    static void chat(Context c, String system, String user, Cb cb) {
        EX.execute(() -> {
            try {
                SharedPreferences p = prefs(c);
                boolean free = p.getString("ai_provider", "free").equals("free");
                String key = free ? null : Vault.get(c);
                String base = p.getString("ai_base", ""), model = p.getString("ai_model", "");
                if (free && base.isEmpty()) base = FREE_BASE;
                if (free && model.isEmpty()) model = FREE_MODEL;
                if (free && !p.getBoolean("free_ok", false)) { cb.done(null, "Turn on free AI first."); return; }
                if ((!free && key == null) || base.isEmpty() || model.isEmpty()) {
                    cb.done(null, "Add your API key in the Kbrd app first.");
                    return;
                }
                JSONArray msgs = new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", system))
                        .put(new JSONObject().put("role", "user").put("content", user));
                JSONObject body = new JSONObject().put("model", model).put("temperature", 0.4)
                        .put("max_tokens", 900).put("messages", msgs);
                String res = http("POST", base + "/chat/completions", key, body.toString());
                String t = new JSONObject(res).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content").trim();
                cb.done(t, null);
            } catch (Err e) {
                cb.done(null, e.getMessage());
            } catch (Exception e) {
                cb.done(null, "Couldn't reach the AI service. Check your connection.");
            }
        });
    }

    static void models(Context c, String base, String typedKey, Cb cb) {
        final String key = typedKey.isEmpty() ? Vault.get(c) : typedKey;
        final String b = base.trim().replaceAll("/+$", "");
        EX.execute(() -> {
            try {
                String res = http("GET", b + "/models", key, null);
                JSONArray d = new JSONObject(res).getJSONArray("data");
                ArrayList<String> ids = new ArrayList<>();
                for (int i = 0; i < d.length(); i++) ids.add(d.getJSONObject(i).getString("id").replaceFirst("^models/", ""));
                Collections.sort(ids);
                cb.done(new JSONArray(ids).toString(), null);
            } catch (Err e) {
                cb.done(null, e.getMessage());
            } catch (Exception e) {
                cb.done(null, "Couldn't load the model list. Check the base URL and your connection.");
            }
        });
    }

    private static String http(String method, String url, String key, String body) throws Exception {
        if (!url.startsWith("https://")) throw new Err("The base URL must start with https://");
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod(method);
        h.setConnectTimeout(15000);
        h.setReadTimeout(45000);
        h.setRequestProperty("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) h.setRequestProperty("Authorization", "Bearer " + key);
        if (body != null) {
            h.setDoOutput(true);
            try (OutputStream o = h.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
        }
        int code = h.getResponseCode();
        String s = read(code < 400 ? h.getInputStream() : h.getErrorStream());
        h.disconnect();
        if (code == 401 || code == 403) throw new Err("The API key was rejected. Check the key and the provider.");
        if (code == 429) throw new Err("Rate limit reached. Free plans allow limited requests, so wait a moment or add your own key in the app.");
        if (code == 404) throw new Err("Model or address not found. Check the model name and base URL.");
        if (code >= 400) throw new Err("The AI service returned an error (" + code + ").");
        return s;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }
}
