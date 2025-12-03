package com.zmh.exam.service;

import com.zmh.exam.entity.ExamRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zmh.exam.vo.StartExamVo;
import com.zmh.exam.vo.SubmitAnswerVo;

import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<ExamRecord> {

    ExamRecord startExam(StartExamVo startExamVo);

    ExamRecord getExamRecordById(Integer id);

    void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers);

    ExamRecord gradeExam(Integer examRecordId) ;
}
 