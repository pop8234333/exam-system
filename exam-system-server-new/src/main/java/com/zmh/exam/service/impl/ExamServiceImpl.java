package com.zmh.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmh.exam.entity.*;
import com.zmh.exam.mapper.AnswerRecordMapper;
import com.zmh.exam.mapper.ExamRecordMapper;
import com.zmh.exam.mapper.PaperQuestionMapper;
import com.zmh.exam.mapper.QuestionAnswerMapper;
import com.zmh.exam.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.vo.GradingResult;
import com.zmh.exam.vo.StartExamVo;
import com.zmh.exam.vo.SubmitAnswerVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {

    private final ExamRecordService examRecordService;
    private final AnswerRecordMapper answerRecordMapper;
    private final PaperService paperService;
    private final KimiAiService kimiAiService;
    private final QuestionService questionService;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final PaperQuestionMapper paperQuestionMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ExamRecord startExam(StartExamVo startExamVo) {

        Integer examId = startExamVo.getPaperId();
        Paper paper = paperService.getById(examId);
        Integer duration = paper.getDuration();

        String studentName = startExamVo.getStudentName();
        ExamRecord examRecord = new ExamRecord();
        //给传输数据字段赋值
        examRecord.setExamId(examId);
        examRecord.setStudentName(studentName);
        //给其他需要给前端返回的数据设置初始值,score,startTime,status,windowSwitches
        examRecord.setScore(0);
        examRecord.setStartTime(LocalDateTime.now());
        examRecord.setEndTime(LocalDateTime.now().plusMinutes(duration));
        examRecord.setStatus("进行中");
        examRecord.setWindowSwitches(0);

        examRecordService.save(examRecord);

        return examRecord;
    }

    @Override
    public ExamRecord getExamRecordById(Integer id) {
        //这里面的id指的是考试记录id即examRecordId
        ExamRecord examRecord = getById(id);
        if (examRecord == null) {
            throw new RuntimeException("id为"+id+"的考试记录不存在");
        }
        //此时还需要给examRecord里面加入答案记录列表和试卷信息
        //从答案记录表中查出答案记录列表
        List<AnswerRecord> answerRecord = answerRecordMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getExamRecordId,examRecord.getId()));
        //将答案记录列表放入examRecord内
        examRecord.setAnswerRecords(answerRecord);

        //从试卷表中查询试卷信息

        Paper paper = paperService.customPaperDetailById(examRecord.getExamId());

        //将试卷信息放入examRecord内
        examRecord.setPaper(paper);


        return examRecord;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers) {
        //试卷提交之后要修改的属性有,答题记录表数据,考试记录表数据
        //答题记录表中要设置修改的属性有:实际得分score,AI智能批改的评价意见aiCorrection,答题正确性isCorrect
        //考试记录表中要设置修改的属性有:实际总得分score,AI对考试的总体评价answers,考试状态status

        //先插入答案记录,方便之后的考试判卷
        List<AnswerRecord> answerRecordList = answers.stream().map(answer ->
                new AnswerRecord(examRecordId, answer.getQuestionId(), answer.getUserAnswer())
        ).toList();
        answerRecordMapper.insert(answerRecordList);

        //根据examRecordId查出对应的完整的考试记录数据
        ExamRecord examRecord = gradeExam(examRecordId);
        //此时的examRecord内有除了考试状态之外所有所需的数据
        List<AnswerRecord> answerRecords = examRecord.getAnswerRecords();
        try {
            answerRecordMapper.updateById(answerRecords);//判卷完之后在批量更新对应的答案记录
        } catch (Exception e) {
            throw new RuntimeException("答案记录更新失败:"+answerRecordList);
        }

        //更新考试状态为完成
        examRecord.setStatus("已批阅");
        //更新考试的结束时间
        examRecord.setEndTime(LocalDateTime.now());
        //更新对应的考试记录
        boolean updated = examRecordService.updateById(examRecord);
        if (!updated) {
            throw new RuntimeException("考试记录更新失败:"+examRecord.getId());
        }


//        //从试卷表中查询试卷信息
//
//        Paper paper = paperService.customPaperDetailById(examRecord.getExamId());
//
//        //将试卷信息放入examRecord内
//        examRecord.setPaper(paper);

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ExamRecord gradeExam(Integer examRecordId)  {
        //获取当前考试记录
        ExamRecord examRecord = getExamRecordById(examRecordId);
        //从根据考试记录id拿到对应的用户答案记录
        List<AnswerRecord> answerRecordList = answerRecordMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getExamRecordId, examRecord.getId()));
        int maxTotalScore = 0;//初始化最大总得分
        for (AnswerRecord answerRecord : answerRecordList) {
            Integer questionId = answerRecord.getQuestionId();
            Question question = questionService.getById(questionId);//获取题目对象
            //获取题目对应的答案对象
            QuestionAnswer questionAnswer = questionAnswerMapper.selectOne(new LambdaQueryWrapper<QuestionAnswer>()
                    .eq(QuestionAnswer::getQuestionId, questionId));
            question.setAnswer(questionAnswer);
            //查询到该题目在此次考试的最大分数
            PaperQuestion paperQuestion = paperQuestionMapper.selectOne(new LambdaQueryWrapper<PaperQuestion>()
                    .eq(PaperQuestion::getQuestionId, questionId)
                    .eq(PaperQuestion::getPaperId, examRecord.getExamId()));
            BigDecimal score = paperQuestion.getScore();
            int maxScore = score.intValue();
            maxTotalScore += maxScore;
            String userAnswer = answerRecord.getUserAnswer();
            String answer = questionAnswer.getAnswer();
            //根据题目类型进行判断答案是否正确和分数
            String type = question.getType();
            switch (type) {
                case "CHOICE":
                    //这里还可以继续判断是否为多选,多选题对一部分能拿部分分
                    if (answer.equalsIgnoreCase(userAnswer)){
                        answerRecord.setIsCorrect(1); //正确
                        //设置分数为该考试真实最大分数
                        answerRecord.setScore(maxScore);
                    }else {
                        answerRecord.setIsCorrect(0); //错误
                        //设置分数为0
                        answerRecord.setScore(0);
                    }
                    break;
                case "JUDGE":
                    userAnswer = normalizeJudgeAnswer(userAnswer);
                    if (answer.equalsIgnoreCase(userAnswer)){
                        answerRecord.setIsCorrect(1);//正确
                        answerRecord.setScore(maxScore);
                    }else {
                        answerRecord.setIsCorrect(0);//错误
                        answerRecord.setScore(0);
                    }
                    break;
                case "TEXT":
                    //简答题使用ai判卷
                    GradingResult gradingResult = kimiAiService.gradingTextQuestion(question,userAnswer,maxScore );//需要传入简答题,用户答案,答案最大分数
                    answerRecord.setAiCorrection(gradingResult.getReason());
                    Integer resultScore = gradingResult.getScore();
                    if (resultScore < maxScore && resultScore > 0) {
                        answerRecord.setIsCorrect(2);//部分正确
                        answerRecord.setScore(resultScore);
                    }else if(resultScore >= maxScore){
                        answerRecord.setIsCorrect(1);
                        answerRecord.setScore(maxScore);
                    }else {
                        answerRecord.setIsCorrect(0);
                        answerRecord.setScore(0);
                    }
                    break;
                default:
                    throw new RuntimeException("未知的题型");
            }
        }
        //试卷判卷完成
        //拿到总得分
        int totalScore = answerRecordList.stream().mapToInt(AnswerRecord::getScore).sum();
        long successCount = answerRecordList.stream()
                .filter(record -> Integer.valueOf(1).equals(record.getIsCorrect()))
                .count();


        //ai生成考试总评
        String summary = kimiAiService.buildSummary
                (totalScore, maxTotalScore, answerRecordList.size(), Math.toIntExact(successCount));//需要传入总得分,最大总分,题目总数,答对题目数
        //设置本场考试评价
        examRecord.setAnswers(summary);
        examRecord.setScore(totalScore);//设置本场考试总得分
        examRecord.setAnswerRecords(answerRecordList);
        return examRecord;
    }

    /**
     * 标准化判断题答案，将T/F转换为TRUE/FALSE
     * @param answer 原始答案
     * @return 标准化后的答案
     */
    private String normalizeJudgeAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return "";
        }

        String normalized = answer.trim().toUpperCase();
        return switch (normalized) {
            case "T", "TRUE", "正确" -> "TRUE";
            case "F", "FALSE", "错" -> "FALSE";
            default -> normalized;
        };
    }
}