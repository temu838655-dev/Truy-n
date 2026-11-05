package com.bokaliai.terminal;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for text extraction from provider API responses. */
public class AiResponseParserTest {
    @Test
    public void extractsGeminiTextPartsInOrder() throws Exception {
        JSONObject response = new JSONObject("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Xin \"},{\"text\":\"chào\"}]}}]}");
        assertEquals("Xin chào", AiResponseParser.extract("Gemini", response, 200));
    }

    @Test
    public void extractsOnlyClaudeTextBlocks() throws Exception {
        JSONObject response = new JSONObject("{\"content\":[{\"type\":\"thinking\",\"thinking\":\"ẩn\"},{\"type\":\"text\",\"text\":\"Bản dịch\"}]}");
        assertEquals("Bản dịch", AiResponseParser.extract("Claude", response, 200));
    }

    @Test
    public void prefersOpenAiOutputTextShortcut() throws Exception {
        JSONObject response = new JSONObject("{\"output_text\":\"Kết quả trực tiếp\",\"output\":[]}");
        assertEquals("Kết quả trực tiếp", AiResponseParser.extract("OpenAI", response, 200));
    }

    @Test
    public void extractsOpenAiNestedMessageText() throws Exception {
        JSONObject response = new JSONObject("{\"output\":[{\"type\":\"reasoning\"},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Dòng một\"},{\"type\":\"text\",\"text\":\"; dòng hai\"}]}]}");
        assertEquals("Dòng một; dòng hai", AiResponseParser.extract("OpenAI", response, 200));
    }

    @Test
    public void limitsHttpErrorDetailToNineHundredCharacters() throws Exception {
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < 1200; i++) detail.append('x');
        JSONObject response = new JSONObject().put("error", detail.toString());
        String answer = AiResponseParser.extract("Gemini", response, 429);
        assertTrue(answer.startsWith("Gemini API HTTP 429: "));
        assertEquals("Gemini API HTTP 429: ".length() + 900, answer.length());
    }

    @Test
    public void reportsEmptyProviderResponseInsteadOfReturningNull() throws Exception {
        String answer = AiResponseParser.extract("Gemini", new JSONObject(), 200);
        assertTrue(answer.startsWith("Gemini không trả nội dung văn bản:"));
    }
}
