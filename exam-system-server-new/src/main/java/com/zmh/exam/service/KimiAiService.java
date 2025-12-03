package com.zmh.exam.service;


import com.zmh.exam.common.Result;
import com.zmh.exam.entity.Question;
import com.zmh.exam.vo.AiGenerateRequestVo;
import com.zmh.exam.vo.GradingResult;
import com.zmh.exam.vo.QuestionImportVo;

import java.util.List;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface KimiAiService {

    String buildPrompt(AiGenerateRequestVo request);

    /**
     * AI生成题目（预览）
     * @param request 生成请求
     * @return 题目列表
     */
    Result<List<QuestionImportVo>> generateQuestionsByAi(AiGenerateRequestVo request);


    /**
     * 使用ai,进行简答题判断
     * @param question 简答题
     * @param userAnswer 用户答案
     * @param maxScore 最大分
     * @return 判卷结构
     */
    GradingResult gradingTextQuestion(Question question, String userAnswer, Integer maxScore) ;

    /**
     * 生成考试总评和建议
     * @param totalScore 总得分
     * @param maxScore 总满分
     * @param questionCount 题目总数
     * @param correctCount 答对题目数
     * @return 考试总评
     */
    String buildSummary(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount) ;
}