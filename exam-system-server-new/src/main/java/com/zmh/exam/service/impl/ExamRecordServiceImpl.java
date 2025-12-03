package com.zmh.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.entity.AnswerRecord;
import com.zmh.exam.entity.ExamRecord;
import com.zmh.exam.entity.Paper;
import com.zmh.exam.mapper.AnswerRecordMapper;
import com.zmh.exam.mapper.ExamRecordMapper;
import com.zmh.exam.service.ExamRecordService;
import com.zmh.exam.service.PaperService;
import com.zmh.exam.vo.ExamRankingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试记录Service实现类
 * 实现考试记录相关的业务逻辑
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamRecordService {

    private final ExamRecordMapper examRecordMapper;
    private final PaperService paperService;
    private final AnswerRecordMapper answerRecordMapper;

    @Override
    public Page<ExamRecord> getExamRecords(Page<ExamRecord> pageBean, String studentName, String studentNumber, Integer status, String startDate, String endDate) {

//        // 前端传的是 2025-12-03 这种格式

        LambdaQueryChainWrapper<ExamRecord> wrapper = lambdaQuery()
                .like(studentName != null && !studentName.isEmpty(), ExamRecord::getStudentName, studentName);

        if (status!=null){
            // 将status转换为对应的字符串
            String strStatus = switch (status) {
                case 0 -> "进行中";
                case 1 -> "已完成";
                case 2 -> "已批阅";
                default -> null;
            };
            wrapper.eq(strStatus != null, ExamRecord::getStatus, strStatus);
        }
        if (startDate != null && !startDate.isEmpty()
                && endDate != null && !endDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(LocalTime.MAX);
            wrapper.between(ExamRecord::getStartTime, startDateTime, endDateTime);
        }

        Page<ExamRecord> examRecordPage = wrapper
                .orderByDesc(ExamRecord::getId)
                .page(pageBean);

        List<ExamRecord> records = pageBean.getRecords();
        List<Integer> paperIds = records.stream()
                .map(ExamRecord::getExamId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!paperIds.isEmpty()) {
            List<Paper> paperList = paperService.listByIds(paperIds);
            Map<Long, Paper> paperMap = paperList.stream()
                    .collect(Collectors.toMap(Paper::getId, Function.identity()));
            records.forEach(r -> r.setPaper(paperMap.get((r.getExamId()).longValue())));
        }

        return examRecordPage;
    }

    @Override
    public void customRemoveById(Integer id) {
        //考试记录删除,删除前需要检查当前考试记录状态,不能是"进行中"状态
        ExamRecord examRecord = getById(id);
        if (examRecord == null) {
            throw new RuntimeException("当前考试id为:"+id+"记录查询不到");
        }
        if (examRecord.getStatus().equals("进行中")){
            throw new RuntimeException("目前考试id为:"+id+"还在进行中,不能删除");
        }

        //删除自身数据，同时删除答题记录
        removeById(id);
        answerRecordMapper.delete(new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId,id));


    }

    @Override
    public List<ExamRankingVO> customGetRanking(Integer paperId, Integer limit) {
        // 根据试卷id查询相关属性,并根据考试分数倒序排序,取前limit条数据,查询结果封装或赋值给ExamRankingVO
        List<ExamRankingVO> rankingList = examRecordMapper.selectRanking(paperId, limit);
        return rankingList == null ? List.of() : rankingList;
    }
}
