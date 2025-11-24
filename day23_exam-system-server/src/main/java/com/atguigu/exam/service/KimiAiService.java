package com.atguigu.exam.service;


import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.GradingResult;
import com.atguigu.exam.vo.QuestionImportVo;

import java.util.List;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface KimiAiService {


    /**
     * 生成ai评语
     * @param totalScore
     * @param maxScore
     * @param questionCount
     * @param correctCount
     * @return
     */
    String buildSummary(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount) throws InterruptedException;


    /**
     * 使用ai,进行简答题判断
     * @param question
     * @param userAnswer
     * @param maxScore
     * @return
     */
     GradingResult gradingTextQuestion(Question question, String userAnswer, Integer maxScore) throws InterruptedException;


    /**
     * 根据前台传递的上下文环境，生成对应的提示词
     * @param request
     * @return
     */
    String buildPrompt(AiGenerateRequestVo request);

    /**
     * 封装调用kimi模型，最终返结果
     * @param prompt
     * @return 返回生成题目json 结果 / choices / message / content
     */
    String callKimiAi(String prompt) throws InterruptedException;

    /**
     * ai题目信息生成
     * @param request
     * @return
     */
    List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request) throws InterruptedException;
}