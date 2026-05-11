package com.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    // 缓存AI解释结果，避免重复调用API
    private final Map<String, Map<String, Object>> explanationCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRE_TIME = 24 * 60 * 60 * 1000; // 24小时
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public AIService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 获取单词的AI智能解释（使用通义千问）
     */
    public Map<String, Object> getAIExplanation(String word) {
        // 检查缓存
        if (explanationCache.containsKey(word)) {
            Long timestamp = cacheTimestamps.get(word);
            if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_EXPIRE_TIME) {
                System.out.println("✅ 使用缓存的AI解释: " + word);
                return explanationCache.get(word);
            }
        }

        if (!aiEnabled || apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("⚠️ AI功能未启用或API Key未配置，返回模拟数据");
            return getMockExplanation(word);
        }

        try {
            System.out.println("🤖 调用通义千问API获取解释: " + word);

            String prompt = String.format(
                    "请为英语单词 \"%s\" 提供详细的学习解释，包括以下内容：\n" +
                            "1. 基本含义\n" +
                            "2. 使用场景（至少3个）\n" +
                            "3. 记忆技巧\n" +
                            "4. 常见错误\n" +
                            "5. 扩展学习建议\n\n" +
                            "请用JSON格式返回，字段名为：basicMeaning, usageScenarios(数组), memoryTips, commonErrors, extendedLearning",
                    word
            );

            String requestBody = String.format(
                    "{\"model\":\"qwen-turbo\",\"input\":{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}}",
                    prompt.replace("\"", "\\\"")
            );

            String response = webClient.post()
                    .uri("/services/aigc/text-generation/generation")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> result = parseAIResponse(response, word);

            // 存入缓存
            explanationCache.put(word, result);
            cacheTimestamps.put(word, System.currentTimeMillis());

            return result;

        } catch (Exception e) {
            System.err.println("❌ AI API调用失败: " + e.getMessage());
            e.printStackTrace();
            return getMockExplanation(word);
        }
    }

    /**
     * 解析AI响应
     */
    private Map<String, Object> parseAIResponse(String response, String word) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.path("output").path("text");

            if (outputNode.isTextual()) {
                String text = outputNode.asText();

                // 尝试从文本中提取JSON
                int jsonStart = text.indexOf("{");
                int jsonEnd = text.lastIndexOf("}");

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String jsonStr = text.substring(jsonStart, jsonEnd + 1);
                    JsonNode jsonNode = objectMapper.readTree(jsonStr);

                    result.put("word", word);
                    result.put("basicMeaning", jsonNode.has("basicMeaning") ?
                            jsonNode.get("basicMeaning").asText() : "暂无");

                    List<String> scenarios = new ArrayList<>();
                    if (jsonNode.has("usageScenarios") && jsonNode.get("usageScenarios").isArray()) {
                        jsonNode.get("usageScenarios").forEach(item ->
                                scenarios.add(item.asText()));
                    }
                    result.put("usageScenarios", scenarios);

                    result.put("memoryTips", jsonNode.has("memoryTips") ?
                            jsonNode.get("memoryTips").asText() : "暂无");
                    result.put("commonErrors", jsonNode.has("commonErrors") ?
                            jsonNode.get("commonErrors").asText() : "暂无");
                    result.put("extendedLearning", jsonNode.has("extendedLearning") ?
                            jsonNode.get("extendedLearning").asText() : "暂无");
                    result.put("timestamp", System.currentTimeMillis());
                    result.put("source", "通义千问AI");

                    return result;
                }
            }

        } catch (Exception e) {
            System.err.println("解析AI响应失败: " + e.getMessage());
        }

        return getMockExplanation(word);
    }

    /**
     * 模拟AI解释（备用方案）
     */
    private Map<String, Object> getMockExplanation(String word) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("word", word);
        result.put("basicMeaning", "这是单词 \"" + word + "\" 的基本含义（模拟数据）");
        result.put("usageScenarios", Arrays.asList(
                "日常对话中常用表达",
                "学术写作中的标准用法",
                "商务场合的正式表达"
        ));
        result.put("memoryTips", "建议通过词根词缀法记忆，结合例句加深理解。");
        result.put("commonErrors", "注意拼写和发音的准确性，避免与相似词混淆。");
        result.put("extendedLearning", "推荐学习相关的同义词、反义词和固定搭配。");
        result.put("timestamp", System.currentTimeMillis());
        result.put("source", "本地模拟");

        return result;
    }

    /**
     * AI跟读评分（简化版，实际需要语音识别服务）
     */
    public Map<String, Object> evaluatePronunciation(String word, String audioData) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("word", word);
        result.put("overallScore", 85);
        result.put("accuracy", 88);
        result.put("fluency", 82);
        result.put("completeness", 90);

        List<Map<String, Object>> feedback = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("phoneme", "/w/");
        item1.put("score", 90);
        item1.put("comment", "发音准确");
        feedback.add(item1);

        result.put("detailedFeedback", feedback);
        result.put("suggestion", "建议多练习元音部分的发音，注意舌位和口型。");
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }
}
