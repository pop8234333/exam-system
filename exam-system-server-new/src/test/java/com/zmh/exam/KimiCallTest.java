package com.zmh.exam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmh.exam.config.WebClientConfiguration;
import com.zmh.exam.config.properties.KimiProperties;
import com.zmh.exam.vo.ChatChoice;
import com.zmh.exam.vo.ChatMessage;
import com.zmh.exam.vo.ChatRequest;
import com.zmh.exam.vo.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@SpringBootTest
public class KimiCallTest {

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(90);
    @Autowired
    WebClient webClient;

    @Autowired
    KimiProperties  kimiProperties;


    @Test
    public void test(){
        // 1) 构建用户提示词

        // 2) 拼装Kimi聊天请求体
        ChatRequest chatRequest = ChatRequest.builder()
                .model(kimiProperties.getModel())
                .messages(List.of(
                        new ChatMessage("system", "你是一名资深出题专家，请严格按要求返回JSON，不要附带解释。"),
                        new ChatMessage("user", "请你生成一段JSON格式的标准信息")
                ))
                .temperature(kimiProperties.getTemperature() == null ? 0.3 : kimiProperties.getTemperature())
                .maxTokens(kimiProperties.getMaxTokens())
                .stream(false)
                .build();

        // 3) 调用Kimi接口并阻塞等待（带超时）
        ChatResponse response = webClient.post()
                .bodyValue(chatRequest)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .block(AI_TIMEOUT);

        System.out.println("response = " + response);

        // 4) 提取和处理响应数据
        extractAndProcessResponse(response);
    }

    /**
     * 提取和处理 ChatResponse 数据的示例方法
     */
    private void extractAndProcessResponse(ChatResponse response) {
        System.out.println("\n========== 开始提取 ChatResponse 数据 ==========");

        // 第一步：验证响应不为空
        if (response == null) {
            System.out.println("✗ 响应为 null");
            return;
        }

        // 第二步：提取基础信息
        System.out.println("\n【基础信息】");
        System.out.println("ID: " + response.getId());
        System.out.println("模型: " + response.getModel());
        System.out.println("对象类型: " + response.getObject());
        System.out.println("创建时间: " + response.getCreated());

        // 第三步：提取 Token 使用情况
        if (response.getUsage() != null) {
            System.out.println("\n【Token 使用统计】");
            System.out.println("提示词 Token: " + response.getUsage().getPromptTokens());
            System.out.println("完成 Token: " + response.getUsage().getCompletionTokens());
            System.out.println("总 Token: " + response.getUsage().getTotalTokens());
        }

        // 第四步：提取选项列表和消息内容
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            System.out.println("\n【选项内容】");
            for (int i = 0; i < response.getChoices().size(); i++) {
                ChatChoice choice = response.getChoices().get(i);
                System.out.println("\n--- 选项 " + (i + 1) + " ---");
                System.out.println("索引: " + choice.getIndex());
                System.out.println("结束原因: " + choice.getFinishReason());

                if (choice.getMessage() != null) {
                    System.out.println("角色: " + choice.getMessage().getRole());
                    System.out.println("内容: " + choice.getMessage().getContent());

                    // 第五步：如果内容是 JSON，进一步解析
                    parseJsonContent(choice.getMessage().getContent());
                }
            }
        } else {
            System.out.println("✗ 响应中没有选项");
        }

        System.out.println("\n========== 数据提取完成 ==========\n");
    }

    /**
     * 解析消息内容中的 JSON 数据
     */
    private void parseJsonContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            System.out.println("\n【尝试解析 JSON 内容】");
            ObjectMapper objectMapper = new ObjectMapper();
            Object jsonObject = objectMapper.readValue(content, Object.class);
            System.out.println("✓ JSON 解析成功");
            System.out.println("格式化输出：" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject));
        } catch (Exception e) {
            System.out.println("⚠ 内容不是有效的 JSON 格式：" + e.getMessage());
            System.out.println("原始内容: " + content);
        }
    }

    @Test
    public void testChat(){



        // 1. 构建消息列表（包含system和user角色）
        List<Map<String, String>> messages = new ArrayList<>();

        // 系统消息
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是 Kimi，由 Moonshot AI 提供的人工智能助手，你更擅长中文和英文的对话。你会为用户提供安全，有帮助，准确的回答。同时，你会拒绝一切涉及恐怖主义，种族歧视，黄色暴力等问题的回答。Moonshot AI 为专有名词，不可翻译成其他语言。");
        messages.add(systemMsg);

        // 用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "张明浩，请问 1+1等于多少？");
        messages.add(userMsg);

        // 2. 构建请求体（包含模型、消息和参数）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "moonshot-v1-32k"); // 指定Kimi模型
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.6); // 温度参数

        Mono<String> mono = webClient.post().bodyValue(requestBody).retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    // 解析响应JSON，提取回答内容
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = null;
                    try {
                        jsonNode = objectMapper.readTree(response);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return jsonNode
                            .get("choices")
                            .get(0)
                            .get("message")
                            .get("content")
                            .asText();
                });

        String block = mono.block();
        System.out.println("block = " + block);
    }
}