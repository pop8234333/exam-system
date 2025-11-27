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
        if (!list.isEmpty()) {
            List<Long> idList = list.stream().map(Question::getId).toList();
            List<Long> categoryIdList = list.stream().map(Question::getCategoryId).filter(Objects::nonNull).toList();

            // 1. 查询答题记录列表 -> List<QuestionAnswer>
            List<QuestionAnswer> answerList = questionAnswerMapper.selectList(
                    new LambdaQueryWrapper<QuestionAnswer>()
                            .in(QuestionAnswer::getQuestionId, idList)
            );

            // 转成 Map<Long, List<QuestionAnswer>>   key = questionId
            Map<Long, QuestionAnswer> answerMap = answerList.stream()
                    .collect(Collectors.toMap(QuestionAnswer::getQuestionId, Function.identity()));


            // 2. 查询选项列表 -> List<QuestionChoice>
            List<QuestionChoice> choiceList = questionChoiceMapper.selectList(
                    new LambdaQueryWrapper<QuestionChoice>()
                            .in(QuestionChoice::getQuestionId, idList)
                            .orderByAsc(QuestionChoice::getSort)
            );

            // 转成 Map<Long, QuestionChoice>   key = questionId
            Map<Long, List<QuestionChoice>> choiceMap = choiceList.stream()
                    .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
            //3.查询题目所属分类信息列表
            List<Category> categoryList = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                    .in(Category::getId, categoryIdList));
            categoryService.fillQuestionCount(categoryList);
            categoryService.buildTree(categoryList);

            Map<Long, Category> categoryMap = categoryList.stream().collect(Collectors.toMap(Category::getId, Function.identity()));


            // 将查询到的信息设置到对应的Question对象中
            for (Question question : list) {
                // 设置答案信息
                QuestionAnswer answers = answerMap.getOrDefault(question.getId(), new QuestionAnswer());
                if (!(answers == null)) {
                    question.setAnswer(answers); // 一个题目只有一个答案
                }

                // 设置选项信息
                question.setChoices(choiceMap.getOrDefault(question.getId(), new ArrayList<>()));

                // 设置分类信息
                if (question.getCategoryId() != null) {
                    question.setCategory(categoryMap.get(question.getCategoryId()));
                }
            }
            log.info("list:{}", list);


        }
        Page<Question> pageResult = pageBean.setRecords(list);


        return pageResult;
    }






}