package com.zmh.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmh.exam.entity.ExamRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zmh.exam.vo.ExamRankingVO;


import java.util.List;

/**
 * 考试记录Service接口
 * 定义考试记录相关的业务方法
 */
public interface ExamRecordService extends IService<ExamRecord> {

    Page<ExamRecord> getExamRecords(Page<ExamRecord> pageBean, String studentName, String studentNumber, Integer status, String startDate, String endDate);

    void customRemoveById(Integer id);

    List<ExamRankingVO> customGetRanking(Integer paperId, Integer limit);
}