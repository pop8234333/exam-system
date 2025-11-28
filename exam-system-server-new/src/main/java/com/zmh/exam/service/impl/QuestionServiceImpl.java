package com.zmh.exam.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmh.exam.common.CacheConstants;
import com.zmh.exam.entity.*;
import com.zmh.exam.mapper.CategoryMapper;
import com.zmh.exam.mapper.QuestionAnswerMapper;
import com.zmh.exam.mapper.QuestionChoiceMapper;
import com.zmh.exam.mapper.QuestionMapper;
import com.zmh.exam.service.CategoryService;
import com.zmh.exam.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.utils.RedisUtils;
import com.zmh.exam.vo.QuestionQueryVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RedisUtils redisUtils;


    @Override
    public Page<Question> getPage(Page<Question> pageBean, QuestionQueryVo questionQueryVo) {
        // 使用三元运算符判断null和空串

        Page<Question> page = lambdaQuery().eq(questionQueryVo.getCategoryId() != null, Question::getCategoryId, questionQueryVo.getCategoryId())
                .eq(questionQueryVo.getDifficulty() != null && !questionQueryVo.getDifficulty().isEmpty(), Question::getDifficulty, questionQueryVo.getDifficulty())
                .eq(questionQueryVo.getType() != null && !questionQueryVo.getType().isEmpty(), Question::getType, questionQueryVo.getType())
                .like(questionQueryVo.getKeyword() != null && !questionQueryVo.getKeyword().isEmpty(), Question::getTitle, questionQueryVo.getKeyword())
                .orderByDesc(Question::getUpdateTime)
                .page(pageBean);//根据更新时间倒序排序

        //目前的list中还没有题目答案和题目选项列表和题目所属分类信息
        //拿到list中的id列表,然后再根据id列表查询对应的信息再赋值
        enrichQuestions(page);


        return page;
    }

    @Override
    public Question customDetailQuestion(Long id) {
        // 1) 查题目本身
        Question question = getById(id);
        if (question == null){
            throw  new RuntimeException("题目查询详情失败！原因可能提前被删除！题目id为：" + id);
        }

        // 2) 答案（一题一答，重复时取第一条）
        QuestionAnswer answer = questionAnswerMapper.selectOne(
                new LambdaQueryWrapper<QuestionAnswer>()
                        .eq(QuestionAnswer::getQuestionId, id)
                        .last("limit 1"));
        if (answer != null) {
            question.setAnswer(answer);
        }

        // 3) 选项（按 sort 升序）
        List<QuestionChoice> choices = questionChoiceMapper.selectList(
                new LambdaQueryWrapper<QuestionChoice>()
                        .eq(QuestionChoice::getQuestionId, id)
                        .orderByAsc(QuestionChoice::getSort));
        question.setChoices(choices);

        // 4) 分类信息
        if (question.getCategoryId() != null) {
            Category category = categoryMapper.selectById(question.getCategoryId());
            question.setCategory(category);
        }
        //2.进行热点题目缓存
        new Thread(() -> {
            incrementQuestion(question.getId());
        }).start();
        return question;
    }
    /**
     * 进行题目信息保存
     * @param question
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customSaveQuestion(Question question) {
        // 0) 入参兜底，避免出现 NPE
        if (question == null) {
            throw new IllegalArgumentException("题目信息不能为空");
        }

        // 1) 校验“同类型 + 同标题”是否已存在，依赖逻辑删除字段自动过滤已删除数据
        boolean exists = lambdaQuery()
                .eq(Question::getType, question.getType())
                .eq(Question::getTitle, question.getTitle())
                .exists();
        if (exists) {
            throw new RuntimeException("同类型下已存在相同标题的题目，保存失败：" + question.getTitle());
        }

        // 2) 先保存题目主体，获取自增的题目 ID，后续子表要用到
        boolean saved = save(question);
        if (!saved || question.getId() == null) {
            throw new RuntimeException("题目主体保存失败，无法继续保存选项与答案");
        }

        // 3) 准备答案对象（非选择题直接使用传入答案；选择题稍后根据正确选项生成）
        QuestionAnswer answer = question.getAnswer() == null ? new QuestionAnswer() : question.getAnswer();
        answer.setQuestionId(question.getId());

        // 4) 如果是选择题：逐条落库选项，并动态拼接标准答案（A/B/C... 多选用逗号分隔）
        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> choices = question.getChoices();
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("选择题至少需要配置一个选项");
            }

            StringBuilder correctAnswer = new StringBuilder();
            for (int i = 0; i < choices.size(); i++) {
                QuestionChoice choice = choices.get(i);
                // 若前端未指定排序，默认用当前下标（从 0 开始）确保选项顺序固定
                int sort = choice.getSort() == null ? i : choice.getSort();
                choice.setSort(sort);
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);

                // 根据正确选项生成答案字母（0->A，1->B...），多选用英文逗号拼接
                if (Boolean.TRUE.equals(choice.getIsCorrect())) {
                    if (!correctAnswer.isEmpty()) {
                        correctAnswer.append(",");
                    }
                    correctAnswer.append((char) ('A' + sort));
                }
            }
            answer.setAnswer(correctAnswer.toString());
        } else if ("JUDGE".equals(question.getType()) && answer.getAnswer() != null) {
            // 判断题统一小写 true/false，避免大小写导致的判题问题
            answer.setAnswer(answer.getAnswer().toLowerCase());
        }

        // 5) 题目答案入库（选择题已在上面生成，其他题型直接使用原答案）
        questionAnswerMapper.insert(answer);
    }

    //定义进行题目访问次数增长的方法
    //异步方法
    private void incrementQuestion(Long questionId){
        Double score = redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY,questionId,1);
        log.info("完成{}题目分数累计，累计后分数为：{}",questionId,score);
    }


    /* 批量查询并回填答案、选项、分类信息 */
    private void enrichQuestions(Page<Question> page) {
        List<Question> questions = page.getRecords();
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
