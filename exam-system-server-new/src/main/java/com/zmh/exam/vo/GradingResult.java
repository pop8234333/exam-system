package com.zmh.exam.vo;

import lombok.Data;

/**
 * 判卷结果内部类
 */
@Data
public  class GradingResult {

    // Getters
    private Integer score;//实际得分(整数)
    private String feedback;//具体的评价反馈(50字以内)
    private String reason;//扣分原因或得分依据(30字以内)

    public GradingResult(Integer score, String feedback, String reason) {
        this.score = score;
        this.feedback = feedback;
        this.reason = reason;
    }

}