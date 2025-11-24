package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.ExamRankingVO;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<ExamRecord> {

    /**
     * 开始考试业务
     * @param startExamVo
     * @return
     */
    ExamRecord startExam(StartExamVo startExamVo);

    /**
     * 获取考试记录详情
     * @param id
     * @return
     */
    ExamRecord customGetExamRecordById(Integer id);

    /**
     * 提交考试答案
     * @param examRecordId
     * @param answers
     */
    void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers) throws InterruptedException;

    /**
     * ai试卷批阅功能
     * @param examRecordId
     * @return
     */
    ExamRecord gradeExam(Integer examRecordId) throws InterruptedException;

    /**
     * 删除考试记录
     * @param id
     */
    void customRemoveById(Integer id);

    /**
     * 查询排行榜业务
     * @param paperId
     * @param limit
     * @return
     */
    List<ExamRankingVO> customGetRanking(Integer paperId, Integer limit);
}
 