package com.zmh.exam.service.impl;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.zmh.exam.entity.Paper;
import com.zmh.exam.entity.PaperQuestion;
import com.zmh.exam.entity.Question;
import com.zmh.exam.mapper.PaperMapper;
import com.zmh.exam.mapper.PaperQuestionMapper;
import com.zmh.exam.service.PaperService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.service.QuestionService;
import com.zmh.exam.vo.PaperVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 试卷服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionService questionService;


    @Override
    public Paper customPaperDetailById(Integer id) {
//        单表java代码进行paper查询
        Paper paper = getById(id);
//        校验paper == null -> 抛异常
        if (paper == null) {
            throw new RuntimeException("不存在id为"+id+"的试卷信息");
        }
//        根据paperId查询题目集合（中间，题目，答案，选项）
        Long paperId = paper.getId();
        List<PaperQuestion> paperQuestionList = new LambdaQueryChainWrapper<>(paperQuestionMapper)
                .eq(PaperQuestion::getPaperId, paperId)
                .list();
        // 判断查询结果是否为空
        //        校验题目集合 == null -> 赋空集合！ log->做好记录
        if (paperQuestionList == null || paperQuestionList.isEmpty()) {
            // 处理空结果的情况
            log.info("未找到试卷ID为{}的题目关联数据", paperId);
            paperQuestionList = new ArrayList<>();
        }


        //拿到对应该试卷的题目id集合
        LinkedHashMap<Long, BigDecimal> questionScoreMap = paperQuestionList.stream()
                .collect(Collectors.toMap(PaperQuestion::getQuestionId,
                        PaperQuestion::getScore,
                        (oldV, newV) -> newV,
                        LinkedHashMap::new));

        List<Long> questionIdList = questionScoreMap.keySet().stream().toList();


        //根据题目id集合拿到对应的题目集合
        List<Question> questions = questionService.listByIds(questionIdList);
        if (questions == null || questions.isEmpty()) {
            log.info("未找到试卷ID为{}的题目数据", paperId);
            questions = new ArrayList<>();
        }
        //遍历题目集合,将查出来的真实试卷分数根据题目id赋值给题目集合
        questions.forEach(question -> {
            BigDecimal bigDecimal = questionScoreMap.get(question.getId());
            if (bigDecimal == null) {
                bigDecimal = new BigDecimal(5);
            }
            question.setPaperScore(bigDecimal);//赋值真实分数
        });
        //        对题目进行排序（选择 -> 判断 -> 简答）
        // 使用自定义顺序按 Question.type 排序，未知或空类型排在最后
        //注意：type排序，是字符类型 -》 字符 -》 对应 -》 固定的数字 1 2 3
        java.util.List<String> typeOrder = java.util.List.of("CHOICE", "JUDGE", "TEXT");
        questions.sort((q1, q2) -> {
            String t1 = q1.getType() == null ? "" : q1.getType().toUpperCase();
            String t2 = q2.getType() == null ? "" : q2.getType().toUpperCase();
            int i1 = typeOrder.indexOf(t1); if (i1 == -1) i1 = Integer.MAX_VALUE;//未知或空类型排在最后
            int i2 = typeOrder.indexOf(t2); if (i2 == -1) i2 = Integer.MAX_VALUE;//未知或空类型排在最后
            return Integer.compare(i1, i2);
        });
        //进行paper题目集合赋值
        paper.setQuestions(questions);
        return paper;
    }

    @Transactional( rollbackFor = Exception.class)
    @Override
    public Paper customCreatePaper(PaperVo paperVo) {
        Paper paper = new Paper();
        //赋值基本字段
        paper.setDescription(paperVo.getDescription());
        paper.setName(paperVo.getName());
        paper.setDuration(paperVo.getDuration());
        //拿到问题id和问题试卷分数集合
        Map<Long, BigDecimal> questionScoreMap = paperVo.getQuestions();
        List<Long> questionIdList = questionScoreMap.keySet().stream().toList();
        //根据问题id集合查出对应的问题集合
        List<Question> questions = questionService.listByIds(questionIdList);
        //给取出来的问题集合赋值真实试卷分数
        BigDecimal totalScore = questions.stream().map(question -> {
            BigDecimal score = questionScoreMap.get(question.getId());
            if (score == null) {
                score = BigDecimal.valueOf(5);
            }
            question.setPaperScore(score);//赋值真实分数
            // map 返回的是这个题目的分数
            return score;
        })  // 把所有题目的分数累加
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        paper.setQuestions(questions);//赋值题目列表
        //还差题目数量,总分,试卷状态没有赋值
        //设置总分
        paper.setTotalScore(totalScore);
        //设置题目数量
        paper.setQuestionCount(questions.size());
        //设置试卷状态(默认发布)
        paper.setStatus("PUBLISHED");
        //paper表插入试卷数据
        paperMapper.insert(paper);

        //将查到的问题id和分数及试卷id封装成PaperQuestion对象集合
        Long paperId = paper.getId();
        List<PaperQuestion> paperQuestionList = questionScoreMap.entrySet().stream()
                .map(entry -> new PaperQuestion(
                        paperId,
                        entry.getKey(),
                        entry.getValue() == null ? BigDecimal.valueOf(5) : entry.getValue()
                )).toList();

        //批量插入数据至PaperQuestion数据表
        paperQuestionMapper.insert(paperQuestionList);

        return paper;
    }
}