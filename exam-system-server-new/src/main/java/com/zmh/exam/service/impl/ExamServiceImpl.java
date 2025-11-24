package com.zmh.exam.service.impl;

import com.zmh.exam.entity.ExamRecord;
import com.zmh.exam.mapper.ExamRecordMapper;
import com.zmh.exam.service.ExamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {

} 