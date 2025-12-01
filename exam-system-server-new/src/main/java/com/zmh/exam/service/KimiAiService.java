package com.zmh.exam.service;


import com.zmh.exam.common.Result;
import com.zmh.exam.vo.AiGenerateRequestVo;
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
}