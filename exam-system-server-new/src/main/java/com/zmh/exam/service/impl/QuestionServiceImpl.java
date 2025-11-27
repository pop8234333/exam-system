package com.zmh.exam.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmh.exam.entity.*;
import com.zmh.exam.mapper.CategoryMapper;
import com.zmh.exam.mapper.QuestionAnswerMapper;
import com.zmh.exam.mapper.QuestionChoiceMapper;
import com.zmh.exam.mapper.QuestionMapper;
import com.zmh.exam.service.CategoryService;
import com.zmh.exam.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.vo.QuestionQueryVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    private final QuestionMapper questionMapper;
    private final CategoryMapper categoryMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionChoiceMapper questionChoiceMapper;
    private final CategoryService categoryService;


    @Override
    public Page<Question> getPage(Page<Question> pageBean, QuestionQueryVo questionQueryVo) {
        // 使用三元运算符判断null和空串

        List<Question> list = lambdaQuery().eq(questionQueryVo.getCategoryId() != null, Question::getCategoryId, questionQueryVo.getCategoryId())
                .eq(questionQueryVo.getDifficulty() != null && !questionQueryVo.getDifficulty().isEmpty(), Question::getDifficulty, questionQueryVo.getDifficulty())
                .eq(questionQueryVo.getType() != null && !questionQueryVo.getType().isEmpty(), Question::getType, questionQueryVo.getType())
                .like(questionQueryVo.getKeyword() != null && !questionQueryVo.getKeyword().isEmpty(), Question::getTitle, questionQueryVo.getKeyword())
                .orderByDesc(Question::getUpdateTime)//根据更新时间倒序排序
                .list(pageBean);
        //目前的list中还没有题目答案和题目选项列表和题目所属分类信息
        //拿到list中的id列表,然后再根据id列表查询对应的信息再赋值
        enrichQuestions(list);


        return pageBean.setRecords(list);
    }


    /* 批量查询并回填答案、选项、分类信息 */
    private void enrichQuestions(List<Question> questions) {
        //拿到list中的id列表,然后再根据id列表查询对应的信息再赋值
        if (questions == null || questions.isEmpty()) {
            return;
        }

        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        List<Long> categoryIds = questions.stream()
                .map(Question::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 1) 答案
        Map<Long, QuestionAnswer> answerMap = questionAnswerMapper.selectList(
                        new LambdaQueryWrapper<QuestionAnswer>().in(QuestionAnswer::getQuestionId, questionIds))
                .stream()
                .collect(Collectors.toMap(QuestionAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        // 2) 选项
        Map<Long, List<QuestionChoice>> choiceMap = questionChoiceMapper.selectList(
                        new LambdaQueryWrapper<QuestionChoice>()
                                .in(QuestionChoice::getQuestionId, questionIds)
                                .orderByAsc(QuestionChoice::getSort))
                .stream()
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));

        // 3) 分类
        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Map.of()
                : categoryMapper.selectList(new LambdaQueryWrapper<Category>().in(Category::getId, categoryIds))
                .stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        // 4) 回填,将查询到的信息设置到对应的Question对象中
        questions.forEach(q -> {
            // 设置答案信息
            QuestionAnswer answer = answerMap.get(q.getId());
            if (answer != null) {
                q.setAnswer(answer);
            }
            // 设置选项信息
            q.setChoices(choiceMap.getOrDefault(q.getId(), new ArrayList<>()));
            // 设置分类信息
            if (q.getCategoryId() != null) {
                q.setCategory(categoryMap.get(q.getCategoryId()));
            }
        });
    }




}