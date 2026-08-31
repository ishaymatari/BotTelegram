package com.pollbot.service;

import com.pollbot.model.Question;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Integrates with OpenAI ChatGPT API to generate poll questions.
 */
public class ChatGPTService {

    private static final Logger log = LoggerFactory.getLogger(ChatGPTService.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final HttpClient httpClient;

    public ChatGPTService(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("YOUR_OPENAI_API_KEY");
    }

    /**
     * Generates poll questions for a given topic using ChatGPT.
     * @param topic The topic for the poll
     * @param questionCount Number of questions to generate (1-3)
     * @return List of generated questions
     */
    public List<Question> generateQuestions(String topic, int questionCount) throws Exception {
        if (!isAvailable()) {
            throw new Exception("OpenAI API key is not configured.");
        }

        String prompt = String.format(
            "Create a poll about the topic: \"%s\".\n" +
            "Generate exactly %d questions.\n" +
            "Each question must have between 2 and 4 answer options.\n" +
            "The questions should be interesting and engaging.\n" +
            "Respond ONLY with valid JSON in this exact format:\n" +
            "{\n" +
            "  \"questions\": [\n" +
            "    {\n" +
            "      \"text\": \"Question text here?\",\n" +
            "      \"options\": [\"Option 1\", \"Option 2\", \"Option 3\", \"Option 4\"]\n" +
            "    }\n" +
            "  ]\n" +
            "}",
            topic, questionCount
        );

        // Build request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("temperature", 0.8);

        JSONArray messages = new JSONArray();

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a helpful assistant that creates engaging polls. Always respond with valid JSON only.");
        messages.put(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.put(userMsg);

        requestBody.put("messages", messages);

        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        // Send HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

        log.info("Sending request to ChatGPT API for topic: {}", topic);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("ChatGPT API error: {} - {}", response.statusCode(), response.body());
            throw new Exception("ChatGPT API returned error " + response.statusCode() + ": " + response.body());
        }

        // Parse response
        JSONObject responseJson = new JSONObject(response.body());
        String content = responseJson
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        log.info("ChatGPT response: {}", content);

        // Parse the generated questions
        JSONObject pollData = new JSONObject(content);
        JSONArray questionsArray = pollData.getJSONArray("questions");

        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < questionsArray.length() && i < 3; i++) {
            JSONObject qObj = questionsArray.getJSONObject(i);
            String text = qObj.getString("text");
            JSONArray optionsArray = qObj.getJSONArray("options");

            List<String> options = new ArrayList<>();
            for (int j = 0; j < optionsArray.length() && j <= 4; j++) {
                options.add(optionsArray.getString(j));
            }

            // Ensure valid bounds
            if (options.size() >= 2 && options.size() <= 4) {
                questions.add(new Question(text, options));
            }
        }

        if (questions.isEmpty()) {
            throw new Exception("ChatGPT did not generate valid questions.");
        }

        log.info("Generated {} questions from ChatGPT", questions.size());
        return questions;
    }
}
