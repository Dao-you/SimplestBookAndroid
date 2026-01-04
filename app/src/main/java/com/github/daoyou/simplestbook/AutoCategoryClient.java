package com.github.daoyou.simplestbook;

import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AutoCategoryClient {

    private static final String TAG = "AutoCategory";
    private static final String MODEL_GITHUB = "openai/gpt-4o-mini";
    private static final String MODEL_OPENAI = "gpt-4o-mini";
    private static final String MODEL_PREF_PREFIX = "auto_category_model_override_";

    private AutoCategoryClient() {
    }

    public static String requestAutoCategory(SharedPreferences prefs, String apiKey, String apiUrl,
                                             int amount, String note, List<String> options) {
        if (options == null || options.isEmpty()
                || apiKey == null || apiKey.trim().isEmpty()
                || apiUrl == null || apiUrl.trim().isEmpty()) {
            Log.d(TAG, "Missing api info or options");
            return null;
        }
        String endpoint = normalizeApiUrl(apiUrl);
        String token = normalizeToken(apiKey);
        if (token.isEmpty()) {
            Log.d(TAG, "Missing token after normalization");
            return null;
        }
        String preferredModel = getPreferredModel(prefs, apiUrl);
        String fallbackModel = preferredModel.equals(MODEL_GITHUB) ? MODEL_OPENAI : MODEL_GITHUB;
        try {
            Log.d(TAG, "Model preferred=" + preferredModel + ", fallback=" + fallbackModel);
            Response primary = requestOnce(endpoint, token, preferredModel, amount, note, options);
            if (primary.isSuccessful()) {
                storePreferredModel(prefs, apiUrl, preferredModel);
                return parseCategoryFromResponse(primary.body, options);
            }
            if (isInvalidModelResponse(primary.code, primary.body)) {
                Log.d(TAG, "Model invalid, retry with " + fallbackModel);
                Response secondary = requestOnce(endpoint, token, fallbackModel, amount, note, options);
                if (secondary.isSuccessful()) {
                    storePreferredModel(prefs, apiUrl, fallbackModel);
                    return parseCategoryFromResponse(secondary.body, options);
                }
                logFailure(secondary);
                return null;
            }
            logFailure(primary);
            return null;
        } catch (Exception e) {
            Log.d(TAG, "Request failed: " + e.getMessage());
            return null;
        }
    }

    private static String normalizeToken(String apiKey) {
        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.startsWith("Bearer ")) {
            return trimmed.substring("Bearer ".length()).trim();
        }
        if (trimmed.startsWith("token ")) {
            return trimmed.substring("token ".length()).trim();
        }
        return trimmed;
    }

    private static String getPreferredModel(SharedPreferences prefs, String apiUrl) {
        String urlKey = normalizeApiUrl(apiUrl);
        String stored = prefs == null ? null : prefs.getString(MODEL_PREF_PREFIX + urlKey, "");
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }
        String url = apiUrl == null ? "" : apiUrl;
        if (url.contains("models.github.ai")) {
            return MODEL_GITHUB;
        }
        return MODEL_OPENAI;
    }

    private static void storePreferredModel(SharedPreferences prefs, String apiUrl, String model) {
        if (prefs == null || model == null || model.trim().isEmpty()) {
            return;
        }
        String key = MODEL_PREF_PREFIX + normalizeApiUrl(apiUrl);
        String existing = prefs.getString(key, "");
        if (!model.equals(existing)) {
            prefs.edit().putString(key, model).apply();
            Log.d(TAG, "Stored preferred model=" + model);
        }
    }

    private static Response requestOnce(String endpoint, String token, String model, int amount, String note,
                                        List<String> options) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SimplestBookAndroid");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("temperature", 0);

            JsonArray messages = new JsonArray();
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "developer");
            systemMessage.addProperty("content", "你是一個記帳助手，請從提供的類別中選出最合適的一個。只輸出 JSON。");
            messages.add(systemMessage);

            StringBuilder optionsText = new StringBuilder();
            for (int i = 0; i < options.size(); i++) {
                if (i > 0) optionsText.append("、");
                optionsText.append(options.get(i));
            }
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", "金額：" + amount + "，描述：" + note
                    + "。可選類別：" + optionsText
                    + "。請輸出 {\"category\":\"類別名稱\"}，只能使用可選類別的其中之一。");
            messages.add(userMessage);
            body.add("messages", messages);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                Log.d(TAG, "Empty response stream, code=" + code);
                return new Response(code, "");
            }
            String response = readAll(stream);
            Log.d(TAG, "Response code=" + code + ", length=" + response.length());
            if (code >= 400) {
                Log.d(TAG, "Response body=" + response);
            }
            return new Response(code, response);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isInvalidModelResponse(int code, String response) {
        if (code != 400 && code != 404) {
            return false;
        }
        if (response == null || response.isEmpty()) {
            return false;
        }
        try {
            JsonElement rootElement = JsonParser.parseString(response);
            if (!rootElement.isJsonObject()) {
                return false;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has("error") || !root.get("error").isJsonObject()) {
                return false;
            }
            JsonObject error = root.getAsJsonObject("error");
            String codeText = error.has("code") ? error.get("code").getAsString() : "";
            String message = error.has("message") ? error.get("message").getAsString() : "";
            String combined = (codeText + " " + message).toLowerCase();
            return combined.contains("invalid model") || combined.contains("invalid_model")
                    || combined.contains("unavailable model") || combined.contains("unavailable_model");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void logFailure(Response response) {
        Log.d(TAG, "Request failed, code=" + response.code);
    }

    private static class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        boolean isSuccessful() {
            return code >= 200 && code < 300;
        }
    }

    private static String normalizeApiUrl(String apiUrl) {
        String trimmed = apiUrl == null ? "" : apiUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/inference/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/chat/completions") || trimmed.endsWith("/responses")) {
            return trimmed;
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        if (trimmed.contains("/v1/")) {
            return trimmed + "/chat/completions";
        }
        return trimmed + "/v1/chat/completions";
    }

    private static String parseCategoryFromResponse(String response, List<String> options) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement rootElement = JsonParser.parseString(response);
            if (rootElement.isJsonObject()) {
                JsonObject root = rootElement.getAsJsonObject();
                if (root.has("choices") && root.get("choices").isJsonArray()) {
                    JsonArray choices = root.getAsJsonArray("choices");
                    if (choices.size() > 0) {
                        JsonObject choice = choices.get(0).getAsJsonObject();
                        String content = "";
                        if (choice.has("message") && choice.get("message").isJsonObject()) {
                            JsonObject message = choice.getAsJsonObject("message");
                            if (message.has("content")) {
                                content = message.get("content").getAsString();
                            }
                        } else if (choice.has("text")) {
                            content = choice.get("text").getAsString();
                        }
                        String parsed = extractCategoryFromContent(content, options);
                        if (parsed != null) return parsed;
                    }
                }
                if (root.has("output") && root.get("output").isJsonArray()) {
                    JsonArray output = root.getAsJsonArray("output");
                    for (int i = 0; i < output.size(); i++) {
                        JsonObject item = output.get(i).isJsonObject() ? output.get(i).getAsJsonObject() : null;
                        if (item == null || !item.has("content") || !item.get("content").isJsonArray()) {
                            continue;
                        }
                        JsonArray content = item.getAsJsonArray("content");
                        for (int j = 0; j < content.size(); j++) {
                            JsonObject part = content.get(j).isJsonObject() ? content.get(j).getAsJsonObject() : null;
                            if (part == null) continue;
                            String text = part.has("text") ? part.get("text").getAsString() : "";
                            String parsed = extractCategoryFromContent(text, options);
                            if (parsed != null) return parsed;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Parse failed: " + e.getMessage());
        }
        return extractCategoryFromContent(response, options);
    }

    private static String extractCategoryFromContent(String content, List<String> options) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (!trimmed.isEmpty()) {
            try {
                JsonElement parsed = JsonParser.parseString(trimmed);
                JsonObject obj = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
                String candidate = obj != null && obj.has("category") ? obj.get("category").getAsString().trim() : "";
                if (!candidate.isEmpty()) {
                    for (String option : options) {
                        if (option.equals(candidate)) {
                            return option;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    JsonElement parsed = JsonParser.parseString(trimmed.substring(start, end + 1));
                    JsonObject obj = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
                    String candidate = obj != null && obj.has("category") ? obj.get("category").getAsString().trim() : "";
                    if (!candidate.isEmpty()) {
                        for (String option : options) {
                            if (option.equals(candidate)) {
                                return option;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            for (String option : options) {
                if (trimmed.equals(option) || trimmed.contains(option)) {
                    return option;
                }
            }
        }
        return null;
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
