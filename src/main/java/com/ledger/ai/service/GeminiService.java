package com.ledger.ai.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@Slf4j
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Send a prompt to Gemini and get back a text response.
     * This is the core method that powers all AI features.
     */
    public String ask(String prompt) {
        try {

            String requestBody = """
    {
        "contents": [{
            "parts": [{
                "text": "%s"
            }]
        }],
        "generationConfig": {
            "temperature": 0.3,
            "maxOutputTokens": 2048
        }
    }
    """.formatted(escapeJson(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_URL + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractText(response.body());
            } else {
                log.error("Gemini API error: {} - {}", response.statusCode(), response.body());
                return "AI analysis unavailable at this time.";
            }

        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage());
            return "AI analysis unavailable at this time.";
        }
    }

    private String extractText(String responseBody) {
    try {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        var parts = json.getAsJsonArray("candidates")
            .get(0).getAsJsonObject()
            .getAsJsonObject("content")
            .getAsJsonArray("parts");

        // Collect all text parts (skip thoughtSignature only parts)
        StringBuilder result = new StringBuilder();
        for (var part : parts) {
            var partObj = part.getAsJsonObject();
            if (partObj.has("text")) {
                result.append(partObj.get("text").getAsString());
            }
        }
        return result.toString().trim();
    } catch (Exception e) {
        log.error("Error parsing Gemini response: {}", e.getMessage());
        return "Could not parse AI response.";
    }
}

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
