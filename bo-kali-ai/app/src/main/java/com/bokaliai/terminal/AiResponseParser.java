package com.bokaliai.terminal;

import org.json.JSONArray;
import org.json.JSONObject;

/** Parses text responses from supported AI APIs without Android UI dependencies. */
public final class AiResponseParser {
    private AiResponseParser() { }

    public static String extract(String provider, JSONObject result, int code) {
        if (result == null) result = new JSONObject();
        if (code < 200 || code >= 300) {
            String detail = result.optString("error", result.toString());
            return provider + " API HTTP " + code + ": "
                + detail.substring(0, Math.min(900, detail.length()));
        }
        if ("Gemini".equals(provider)) {
            JSONArray candidates = result.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject first = candidates.optJSONObject(0);
                JSONObject content = first == null ? null : first.optJSONObject("content");
                JSONArray parts = content == null ? null : content.optJSONArray("parts");
                StringBuilder answer = new StringBuilder();
                if (parts != null) for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null) answer.append(part.optString("text", ""));
                }
                if (answer.length() > 0) return answer.toString();
            }
        } else if ("Claude".equals(provider)) {
            JSONArray content = result.optJSONArray("content");
            StringBuilder answer = new StringBuilder();
            if (content != null) for (int i = 0; i < content.length(); i++) {
                JSONObject part = content.optJSONObject(i);
                if (part != null && "text".equals(part.optString("type")))
                    answer.append(part.optString("text", ""));
            }
            if (answer.length() > 0) return answer.toString();
        } else {
            String directText = result.optString("output_text", "");
            if (!directText.isEmpty()) return directText;
            JSONArray output = result.optJSONArray("output");
            StringBuilder answer = new StringBuilder();
            if (output != null) {
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    if (item == null || !"message".equals(item.optString("type"))) continue;
                    JSONArray content = item.optJSONArray("content");
                    if (content == null) continue;
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject part = content.optJSONObject(j);
                        if (part != null && ("output_text".equals(part.optString("type"))
                            || "text".equals(part.optString("type"))))
                            answer.append(part.optString("text", ""));
                    }
                }
            }
            if (answer.length() > 0) return answer.toString();
        }
        return provider + " không trả nội dung văn bản: " + result.toString();
    }
}
