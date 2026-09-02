package com.example.service.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * {@link AiClient} backed by OpenAI's Chat Completions API.
 */
@Component("openaiClient")
public class OpenAiClient implements AiClient {

    private static final String MODEL = "gpt-4o-mini";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    @Override
    public String providerName() {
        return "OpenAI";
    }

    @Override
    public String requiredEnvVar() {
        return "OPENAI_API_KEY";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AiResponse chat(List<Map<String, Object>> messages) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);

        JsonArray messagesArray = new JsonArray();
        for (Map<String, Object> msg : messages) {
            JsonObject msgNode = new JsonObject();
            msgNode.addProperty("role", (String) msg.get("role"));
            msgNode.addProperty("content", (String) msg.get("content"));
            messagesArray.add(msgNode);
        }
        requestBody.add("messages", messagesArray);

        String requestBodyStr = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " " + response.body());
        }

        JsonObject apiResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        String text = apiResponse.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();

        int inputTokens = 0;
        int outputTokens = 0;
        JsonElement usage = apiResponse.get("usage");
        if (usage != null && !usage.isJsonNull()) {
            JsonObject usageObj = usage.getAsJsonObject();
            inputTokens = usageObj.get("prompt_tokens").getAsInt();
            outputTokens = usageObj.get("completion_tokens").getAsInt();
        }

        return new AiResponse(text, inputTokens, outputTokens);
    }
}
