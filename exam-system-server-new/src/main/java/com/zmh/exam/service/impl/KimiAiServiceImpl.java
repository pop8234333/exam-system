package com.zmh.exam.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zmh.exam.common.Result;
import com.zmh.exam.config.properties.KimiProperties;
import com.zmh.exam.service.KimiAiService;
import com.zmh.exam.vo.AiGenerateRequestVo;
import com.zmh.exam.vo.ChatMessage;
import com.zmh.exam.vo.ChatRequest;
import com.zmh.exam.vo.ChatResponse;
import com.zmh.exam.vo.QuestionImportVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Kimi AI服务实现类
 * 调用Kimi API智能生成题目
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KimiAiServiceImpl implements KimiAiService {

    private final KimiProperties kimiProperties;

    private final WebClient webClient;

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(90);

    /**
     * 构建发送给AI的提示词
     */
    public String buildPrompt(AiGenerateRequestVo request) {
        StringBuilder prompt = new StringBuilder();

        // 说明生成数量与主题
        prompt.append("请为我生成").append(request.getCount()).append("道关于【")
                .append(request.getTopic()).append("】的题目。\n\n");

        // 基础要求标题
        prompt.append("要求：\n");

        // 题目类型要求
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            String[] typeList = request.getTypes().split(",");
            prompt.append("- 题目类型：");
            for (String type : typeList) {
                switch (type.trim()) {
                    case "CHOICE":
                        prompt.append("选择题");
                        if (request.getIncludeMultiple() != null && request.getIncludeMultiple()) {
                            prompt.append("(包含单选和多选)");
                        }
                        prompt.append(" ");
                        break;
                    case "JUDGE":
                        prompt.append("判断题（**重要：确保正确答案和错误答案的数量大致平衡，不要全部都是正确或错误**） ");
                        break;
                    case "TEXT":
                        prompt.append("简答题 ");
                        break;
                }
            }
            prompt.append("\n");
        }

        // 难度要求
        if (request.getDifficulty() != null) {
            String difficultyText = switch (request.getDifficulty()) {
                case "EASY" -> "简单";
                case "MEDIUM" -> "中等";
                case "HARD" -> "困难";
                default -> "中等";
            };
            prompt.append("- 难度等级：").append(difficultyText).append("\n");
        }

        // 额外要求
        if (request.getRequirements() != null && !request.getRequirements().isEmpty()) {
            prompt.append("- 特殊要求：").append(request.getRequirements()).append("\n");
        }

        // 判断题特别要求
        if (request.getTypes() != null && request.getTypes().contains("JUDGE")) {
            prompt.append("- **判断题特别要求**：\n");
            prompt.append("  * 确保生成的判断题中，正确答案(TRUE)和错误答案(FALSE)的数量尽量平衡\n");
            prompt.append("  * 不要所有判断题都是正确的或都是错误的\n");
            prompt.append("  * 错误的陈述应该是常见的误解或容易混淆的概念\n");
            prompt.append("  * 正确的陈述应该是重要的基础知识点\n");
        }

        // 规范返回格式，明确为纯JSON
        prompt.append("\n请严格按照以下JSON格式返回，不要包含任何其他文字：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"title\": \"题目内容\",\n");
        prompt.append("      \"type\": \"CHOICE|JUDGE|TEXT\",\n");
        prompt.append("      \"multi\": true/false,\n");
        prompt.append("      \"difficulty\": \"EASY|MEDIUM|HARD\",\n");
        prompt.append("      \"score\": 5,\n");
        prompt.append("      \"choices\": [\n");
        prompt.append("        {\"content\": \"选项内容\", \"isCorrect\": true/false, \"sort\": 1}\n");
        prompt.append("      ],\n");
        prompt.append("      \"answer\": \"TRUE或FALSE(判断题专用)|文本答案(简答题专用)\",\n");
        prompt.append("      \"analysis\": \"题目解析\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("注意：\n");
        prompt.append("1. 选择题必须有choices数组，判断题和简答题设置answer字段\n");
        prompt.append("2. 多选题的multi字段设为true，单选题设为false\n");
        prompt.append("3. **判断题的answer字段只能是\"TRUE\"或\"FALSE\"，请确保答案分布合理**\n");
        prompt.append("4. 每道题都要有详细的解析\n");
        prompt.append("5. 题目要有实际价值，贴近实际应用场景\n");
        prompt.append("6. 严格按照JSON格式返回，确保可以正确解析\n");

        // 如果只生成判断题，额外强调答案平衡
        if (request.getTypes() != null && request.getTypes().equals("JUDGE") && request.getCount() > 1) {
            prompt.append("7. **判断题答案分布要求**：在").append(request.getCount()).append("道判断题中，");
            int halfCount = request.getCount() / 2;
            if (request.getCount() % 2 == 0) {
                prompt.append("请生成").append(halfCount).append("道正确(TRUE)和").append(halfCount).append("道错误(FALSE)的题目");
            } else {
                prompt.append("请生成约").append(halfCount).append("-").append(halfCount + 1).append("道正确(TRUE)和约").append(halfCount).append("-").append(halfCount + 1).append("道错误(FALSE)的题目");
            }
        }

        return prompt.toString();
    }

    /**
     * AI生成题目
     */
    @Override
    public Result<List<QuestionImportVo>> generateQuestionsByAi(AiGenerateRequestVo request) {
        log.info("收到AI生成题目请求，topic={}，count={}，types={}", request.getTopic(), request.getCount(), request.getTypes());
        try {
            // 1) 构建用户提示词
            String prompt = buildPrompt(request);
            // 2) 拼装Kimi聊天请求体
            ChatRequest chatRequest = ChatRequest.builder()
                    .model(kimiProperties.getModel())
                    .messages(List.of(
                            new ChatMessage("system", "你是一名资深出题专家，请严格按要求返回JSON，不要附带解释。"),
                            new ChatMessage("user", prompt)
                    ))
                    .temperature(kimiProperties.getTemperature() == null ? 0.3 : kimiProperties.getTemperature())
                    .maxTokens(kimiProperties.getMaxTokens())
                    .stream(false)
                    .build();
            System.out.println("chatRequest = " + chatRequest);
            // 3) 调用Kimi接口并阻塞等待（带超时）
            ChatResponse response = webClient.post()
                    .bodyValue(chatRequest)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block(AI_TIMEOUT);

            // 4) 判空：没有choices视为无效响应
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                log.warn("AI响应为空或不包含有效内容");
                return Result.error("AI未返回题目，请稍后再试");
            }

            // 5) 取首条回复内容并解析为题目列表
            String aiContent = response.getChoices().get(0).getMessage().getContent();
            List<QuestionImportVo> questions = parseQuestions(aiContent, request);
            if (questions.isEmpty()) {
                log.warn("AI返回内容解析为空，原始内容片段：{}", abbreviate(aiContent));
                return Result.error("AI返回内容无法解析，请调整提示后重试");
            }
            // 6) 正常返回
            log.info("AI生成题目成功，条数：{}", questions.size());
            return Result.success(questions, "生成成功");
        } catch (WebClientResponseException e) {
            log.error("调用Kimi API失败，status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return Result.error("Kimi接口调用失败：" + e.getStatusCode());
        } catch (Exception e) {
            // 7) 针对超时单独提示，其他异常原样返回
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof TimeoutException) {
                log.error("Kimi API调用超时，已等待 {} 秒", AI_TIMEOUT.getSeconds());
                return Result.error("AI生成超时，请稍后重试或减少题目数量");
            }
            log.error("AI生成题目失败", e);
            return Result.error("AI生成题目失败：" + e.getMessage());
        }
    }


    /**
     * 清理AI返回中的代码块符号，避免解析失败
     */
    private String stripCodeFence(String content) {
        if (content == null) {
            return "";
        }
        // 去除首尾空白
        String cleaned = content.trim();
        // 没有代码块标记直接返回
        if (!cleaned.contains("```")) {
            return cleaned;
        }
        // 记录首末代码块位置
        int first = cleaned.indexOf("```");
        int last = cleaned.lastIndexOf("```");
        // 只有一个```，做简单替换
        if (first == last) {
            return cleaned.replace("```json", "").replace("```", "").trim();
        }
        // 提取三引号中的正文
        String snippet = cleaned.substring(first + 3, last).trim();
        // 可能出现```json开头，剔除语言标记
        if (snippet.startsWith("json")) {
            snippet = snippet.substring(4).trim();
        }
        return snippet;
    }

    /**
     * 将AI返回的JSON内容转成导入所需的题目列表
     */
    private List<QuestionImportVo> parseQuestions(String aiContent, AiGenerateRequestVo request) {
        if (aiContent == null || aiContent.trim().isEmpty()) {
            return List.of();
        }
        // 去掉```包裹，并尝试解析JSON
        String cleaned = stripCodeFence(aiContent);
        JSONArray questionArray = extractQuestionArray(cleaned);
        if (questionArray == null || questionArray.isEmpty()) {
            return List.of();
        }

        List<QuestionImportVo> result = new ArrayList<>();
        for (int i = 0; i < questionArray.size(); i++) {
            JSONObject obj = questionArray.getJSONObject(i);
            if (obj == null) {
                continue;
            }
            // 基础字段赋值
            QuestionImportVo vo = new QuestionImportVo();
            vo.setTitle(obj.getString("title"));
            String type = normalizeType(obj.getString("type"), request);
            vo.setType(type);
            vo.setCategoryId(request.getCategoryId());
            vo.setDifficulty(normalizeDifficulty(obj.getString("difficulty"), request));
            Integer score = obj.getInteger("score");
            vo.setScore(score == null ? 5 : score);
            vo.setAnalysis(obj.getString("analysis"));

            if ("CHOICE".equals(type)) {
                // 选择题处理：多选标识、选项解析、正确数兜底
                vo.setMulti(resolveMulti(obj, request));
                JSONArray choiceArray = obj.getJSONArray("choices");
                if (choiceArray == null) {
                    choiceArray = obj.getJSONArray("options");
                }
                List<QuestionImportVo.ChoiceImportDto> choices = buildChoices(choiceArray);
                if (choices.isEmpty()) {
                    log.warn("第{}题缺少选项，已跳过", i + 1);
                    continue;
                }
                long correctCount = choices.stream().filter(c -> Boolean.TRUE.equals(c.getIsCorrect())).count();
                if (correctCount > 1 && !Boolean.TRUE.equals(vo.getMulti())) {
                    vo.setMulti(true);
                }
                if (correctCount == 0) {
                    choices.get(0).setIsCorrect(true);
                }
                vo.setChoices(choices);
            } else {
                // 非选择题：设置答案、关键词
                vo.setMulti(false);
                vo.setAnswer(normalizeAnswer(obj.getString("answer"), type));
                vo.setKeywords(obj.getString("keywords"));
            }
            result.add(vo);
        }
        return result;
    }

    private JSONArray extractQuestionArray(String jsonText) {
        try {
            // 尝试按对象解析并获取 questions 节点
            JSONObject root = JSON.parseObject(jsonText);
            JSONArray questions = root.getJSONArray("questions");
            if (questions != null) {
                return questions;
            }
            return root.getJSONArray("data");
        } catch (Exception e) {
            log.warn("AI响应解析为对象失败，将尝试数组解析：{}", e.getMessage());
        }
        try {
            // 直接按数组解析（部分模型直接返回数组）
            return JSON.parseArray(jsonText);
        } catch (Exception e) {
            log.warn("AI响应不符合JSON数组格式：{}", e.getMessage());
            return null;
        }
    }

    private List<QuestionImportVo.ChoiceImportDto> buildChoices(JSONArray choiceArray) {
        List<QuestionImportVo.ChoiceImportDto> choices = new ArrayList<>();
        if (choiceArray == null || choiceArray.isEmpty()) {
            return choices;
        }
        for (int j = 0; j < choiceArray.size(); j++) {
            JSONObject cobj = choiceArray.getJSONObject(j);
            if (cobj == null) {
                continue;
            }
            // 逐个映射选项字段
            QuestionImportVo.ChoiceImportDto choice = new QuestionImportVo.ChoiceImportDto();
            choice.setContent(cobj.getString("content"));
            Boolean isCorrect = cobj.getBoolean("isCorrect");
            if (isCorrect == null && cobj.containsKey("correct")) {
                isCorrect = cobj.getBoolean("correct");
            }
            choice.setIsCorrect(isCorrect != null && isCorrect);
            Integer sort = cobj.getInteger("sort");
            choice.setSort(sort == null ? j : sort);
            choices.add(choice);
        }
        return choices;
    }

    private boolean resolveMulti(JSONObject obj, AiGenerateRequestVo request) {
        // 优先取模型返回的multi字段
        Boolean multi = obj.getBoolean("multi");
        if (multi != null) {
            return multi;
        }
        // 其次参考用户请求的includeMultiple
        if (request.getIncludeMultiple() != null) {
            return request.getIncludeMultiple();
        }
        return false;
    }

    private String normalizeType(String type, AiGenerateRequestVo request) {
        // 优先使用模型返回的type，做多语言兼容
        if (type != null) {
            String upper = type.trim().toUpperCase();
            if (upper.contains("CHOICE") || upper.contains("选择")) {
                return "CHOICE";
            }
            if (upper.contains("JUDGE") || upper.contains("判断") || upper.contains("TRUE") || upper.contains("FALSE")) {
                return "JUDGE";
            }
            if (upper.contains("TEXT") || upper.contains("简答") || upper.contains("问答")) {
                return "TEXT";
            }
        }
        // 模型未返回时，按请求的第一个类型兜底
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            String[] split = request.getTypes().split(",");
            if (split.length > 0) {
                return split[0].trim().toUpperCase();
            }
        }
        return "CHOICE";
    }

    private String normalizeDifficulty(String difficulty, AiGenerateRequestVo request) {
        // 模型返回难度时，做常见关键词兼容
        if (difficulty != null && !difficulty.isEmpty()) {
            String upper = difficulty.trim().toUpperCase();
            if (upper.contains("EASY") || upper.contains("简单")) {
                return "EASY";
            }
            if (upper.contains("HARD") || upper.contains("难")) {
                return "HARD";
            }
            if (upper.contains("MEDIUM") || upper.contains("中")) {
                return "MEDIUM";
            }
        }
        // 未返回时采用请求值或默认中等
        if (request.getDifficulty() != null && !request.getDifficulty().isEmpty()) {
            return request.getDifficulty().toUpperCase();
        }
        return "MEDIUM";
    }

    private String normalizeAnswer(String answer, String type) {
        if (answer == null) {
            return null;
        }
        // 非判断题直接透传
        if (!"JUDGE".equals(type)) {
            return answer;
        }
        // 判断题做TRUE/FALSE归一
        String trimmed = answer.trim();
        String upper = trimmed.toUpperCase();
        if ("TRUE".equals(upper) || "T".equals(upper) || "YES".equals(upper) || "正确".equals(trimmed) || "对".equals(trimmed)) {
            return "TRUE";
        }
        if ("FALSE".equals(upper) || "F".equals(upper) || "NO".equals(upper) || "错误".equals(trimmed) || "错".equals(trimmed)) {
            return "FALSE";
        }
        return upper;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        // 避免日志过长，仅截取前200字符
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
