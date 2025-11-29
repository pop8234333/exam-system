package com.zmh.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmh.exam.common.CacheConstants;
import com.zmh.exam.common.Result;
import com.zmh.exam.entity.*;
import com.zmh.exam.mapper.*;
import com.zmh.exam.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.utils.ExcelUtil;
import com.zmh.exam.utils.RedisUtils;
import com.zmh.exam.vo.AiGenerateRequestVo;
import com.zmh.exam.vo.QuestionQueryVo;
import com.zmh.exam.vo.QuestionImportVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.bean.BeanUtil;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private final CategoryMapper categoryMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionChoiceMapper questionChoiceMapper;
    private final RedisUtils redisUtils;
    private final PaperQuestionMapper paperQuestionMapper;


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
        enrichQuestions(page.getRecords());


        return page;
    }

    @Override
    public Question customDetailQuestion(Long id) {
        // 1) 查题目本身
        Question question = getById(id);
        if (question == null){
            throw  new RuntimeException("题目查询详情失败！原因可能提前被删除！题目id为：" + id);
        }

        // 2) 统一使用批量填充方法，避免逐条查导致的潜在 N+1 问题
        enrichQuestions(List.of(question));
        //2.进行热点题目缓存
        new Thread(() -> incrementQuestion(question.getId())).start();
        return question;
    }
    /**
     * 进行题目信息保存
     * @param question
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customSaveQuestion(Question question) {
        // 0) 保护性校验与类型解析，借助枚举保证类型合法性
        if (question == null) {
            throw new IllegalArgumentException("题目信息不能为空");
        }
        QuestionType typeEnum = resolveType(question.getType());
        // 持久化前统一类型格式，避免大小写导致的判重/查询问题
        question.setType(typeEnum.name());

        // 1) 校验“同类型 + 同标题”唯一
        boolean exists = lambdaQuery()
                .eq(Question::getType, typeEnum.name())
                .eq(Question::getTitle, question.getTitle())
                .exists();
        if (exists) {
            throw new RuntimeException("同类型下已存在相同标题的题目，保存失败：" + question.getTitle());
        }

        // 2) 先落库题目主体，拿到自增ID
        boolean saved = save(question);
        if (!saved || question.getId() == null) {
            throw new RuntimeException("题目主体保存失败，无法继续保存选项与答案");
        }

        // 3) 组装答案对象（选择题稍后由选项生成）
        QuestionAnswer answer = question.getAnswer() == null ? new QuestionAnswer() : question.getAnswer();
        answer.setQuestionId(question.getId());
        // 新增时忽略前端传入的答案ID，避免误更新旧记录
        answer.setId(null);

        // 4) 各题型分支处理
        if (typeEnum == QuestionType.CHOICE) {
            handleChoiceForSave(question, answer);
        } else {
            // 非选择题必须有答案；判断题统一大写 TRUE/FALSE
            if (answer.getAnswer() == null || answer.getAnswer().trim().isEmpty()) {
                throw new RuntimeException("非选择题必须填写答案");
            }
            if (typeEnum == QuestionType.JUDGE) {
                answer.setAnswer(answer.getAnswer().toUpperCase());
            }
        }

        // 5) 答案落库
        questionAnswerMapper.insert(answer);
    }
    /**
     * 进行题目信息更新
     * @param question
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customUpdateQuestion(Question question) {

        // 0) 保护性校验与类型解析
        if (question == null || question.getId() == null) {
            throw new IllegalArgumentException("题目或题目ID不能为空");
        }
        QuestionType typeEnum = resolveType(question.getType());
        // 更新时同样规范化类型存储
        question.setType(typeEnum.name());

        // 1) 检验同类型+标题唯一(排除自己)
        boolean exists = lambdaQuery()
                .eq(Question::getType, typeEnum.name())
                .eq(Question::getTitle, question.getTitle())
                .ne(Question::getId, question.getId())
                .exists();
        if (exists) {
            throw new RuntimeException("同类型下已存在相同标题的题目,修改失败:"+question.getTitle());
        }
        // 2) 更新题目主体
        boolean updated = updateById(question);
        if (!updated || question.getId() == null) {
            throw new RuntimeException("题目更新失败:"+question.getId());
        }
        // 3) 准备答案
        QuestionAnswer answer = question.getAnswer() == null ? new QuestionAnswer() : question.getAnswer();
        answer.setQuestionId(question.getId());

        // 4) 选择题：先删旧选项再插入新选项，并重算正确答案
        if (typeEnum == QuestionType.CHOICE) {
            questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>()
                    .eq(QuestionChoice::getQuestionId, question.getId()));
            handleChoiceForSave(question, answer);
        } else if (typeEnum == QuestionType.JUDGE) {
            if (answer.getAnswer() == null || answer.getAnswer().trim().isEmpty()) {
                throw new RuntimeException("非选择题必须填写答案");
            }
            answer.setAnswer(answer.getAnswer().toUpperCase());
        } else {
            if (answer.getAnswer() == null || answer.getAnswer().trim().isEmpty()) {
                throw new RuntimeException("非选择题必须填写答案");
            }
        }

        // 5) 更新答案（已存在则 updateById，否则 insert）
        if (answer.getId() != null) {
            questionAnswerMapper.updateById(answer);
        }else {
            questionAnswerMapper.insert(answer);
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customDeleteQuestion(Long id) {
        // 四、题目删除
        // 关键点1:先检查当前题目是否被试卷引用,未被引用的题目才可以删除
        boolean paperQuestionExists= paperQuestionMapper.exists(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getQuestionId, id));

        if (paperQuestionExists) {
            throw new RuntimeException("当前题目被试卷引用");
        }
        //下面就开始进行删除逻辑
        //删除必须先删除与题目表相关联的附属数据:比如题目答案表数据,题目选项表数据!!!!
        //题目答案数据表一定有,必删除
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, id));
        // 不管是不是选择题，直接删除相关的选择题选项
        // 如果没有匹配的记录，delete方法也不会报错，只是返回删除的记录数为0
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, id));

        boolean success = removeById(id);
        if (!success) {
            throw new RuntimeException("题目删除失败");
        }

        // 关键点2:先删除关联数据,再删除题目本身
        //        答案(一定有)(逻辑删除)
        //        选项(选择题有)(逻辑删除)
        // 关键点3:使用逻辑删除
    }

    @Override
    public List<Question> getPopularQuestions(Integer size) {
        // 1) 校验入参：null或非法则用默认值；同时设置硬性上限防止一次取太多
        int limit = size == null || size <= 0 ? CacheConstants.POPULAR_QUESTIONS_COUNT : size;
        limit = Math.min(limit, 100);

        // 2) 先按热度从Redis获取热门题目ID列表（分数倒序），解析失败的ID会被跳过
        List<Long> hotIds = new ArrayList<>();
        try {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisUtils.zReverseRangeWithScores(CacheConstants.POPULAR_QUESTIONS_KEY, 0, limit - 1);
            if (tuples != null) {
                for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                    if (tuple == null || tuple.getValue() == null) {
                        continue;
                    }
                    try {
                        hotIds.add(Long.parseLong(tuple.getValue().toString()));
                    } catch (NumberFormatException ex) {
                        log.warn("热门题目ID解析失败，已跳过：{}", tuple.getValue()); // 容错：Redis里可能混入异常值
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从Redis获取热门题目失败，将直接使用最新题目。", e); // Redis异常不影响整体功能，继续用DB兜底
        }

        // 3) 按热度ID顺序查询题目，保证返回顺序与热度一致
        List<Question> result = new ArrayList<>();
        if (!hotIds.isEmpty()) {
            List<Question> hotQuestions = lambdaQuery().in(Question::getId, hotIds).list();
            Map<Long, Question> hotMap = hotQuestions.stream()
                    .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));
            hotIds.forEach(id -> {
                Question question = hotMap.get(id);
                if (question != null) {
                    result.add(question);
                } else {
                    // 缓存中存在但数据库已删除的ID，立即清理缓存，防止反复命中无效数据
                    redisUtils.zRemove(CacheConstants.POPULAR_QUESTIONS_KEY, id);
                    log.info("移除已失效的热门题目ID缓存：{}", id);
                }
            });
        }

        // 4) 数量不足时，用最新题目按创建时间倒序补足，且排除已有热门题目避免重复
        if (result.size() < limit) {
            int need = limit - result.size();
            List<Long> excludeIds = result.stream()
                    .map(Question::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<Question> latest = lambdaQuery()
                    .notIn(!excludeIds.isEmpty(), Question::getId, excludeIds)
                    .orderByDesc(Question::getCreateTime)
                    .last("LIMIT " + need)
                    .list();
            result.addAll(latest);
        }

        // 5) 批量补充答案、选项、分类等详情，避免N+1
        enrichQuestions(result);
        return result;
    }

    @Override
    public int refreshPopularQuestions() {
        // 1) 清空旧的热门排行
        redisUtils.delete(CacheConstants.POPULAR_QUESTIONS_KEY);
        // 2) 用最新题目初始化排行榜，分数置0，避免空榜导致查询不到数据
        List<Question> latest = lambdaQuery()
                .orderByDesc(Question::getCreateTime)
                .last("LIMIT " + CacheConstants.POPULAR_QUESTIONS_COUNT)
                .list();
        latest.forEach(question -> redisUtils.zAdd(CacheConstants.POPULAR_QUESTIONS_KEY, question.getId(), 0D));
        return latest.size();
    }

    /**
     * 生成Excel导入模板，供前端下载
     */
    @Override
    public byte[] generateImportTemplate() {
        try {
            byte[] data = ExcelUtil.generateTemplate();
            log.info("生成题目导入模板成功，字节大小：{}", data.length);
            return data;
        } catch (Exception e) {
            log.error("生成导入模板失败", e);
            throw new RuntimeException("生成模板失败：" + e.getMessage());
        }
    }

    /**
     * 预览Excel导入：仅解析与校验，不落库
     */
    @Override
    public Result<List<QuestionImportVo>> previewImport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请上传有效的Excel文件");
        }
        try {
            List<QuestionImportVo> questions = ExcelUtil.parseExcel(file);
            log.info("预览Excel开始，文件名：{}，初步解析行数：{}", file.getOriginalFilename(), questions.size());
            String error = validateList(questions);
            if (error != null) {
                return Result.error(error);
            }
            log.info("预览Excel校验通过，记录数：{}", questions.size());
            return Result.success(questions);
        } catch (Exception e) {
            log.error("预览Excel失败", e);
            return Result.error("解析Excel失败：" + e.getMessage());
        }
    }

    /**
     * 从Excel直接导入并落库
     */
    @Override
    public Result<String> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请上传有效的Excel文件");
        }
        try {
            List<QuestionImportVo> questions = ExcelUtil.parseExcel(file);
            log.info("Excel导入开始，文件名：{}，解析到记录数：{}", file.getOriginalFilename(), questions.size());
            return importQuestions(questions);
        } catch (Exception e) {
            log.error("Excel导入失败", e);
            return Result.error("Excel导入失败：" + e.getMessage());
        }
    }

    /**
     * 批量导入题目（支持Excel或AI生成的列表）
     */
    @Override
    public Result<String> importQuestions(List<QuestionImportVo> questions) {
        String error = validateList(questions);
        if (error != null) {
            return Result.error(error);
        }
        try {
            log.info("批量导入题目开始，数量：{}", questions.size());
            for (QuestionImportVo vo : questions) {
                Question question = convertToQuestion(vo);
                customSaveQuestion(question);
            }
            log.info("批量导入题目完成，成功数量：{}", questions.size());
            return Result.success("导入成功");
        } catch (Exception e) {
            log.error("批量导入题目失败", e);
            return Result.error("批量导入题目失败：" + e.getMessage());
        }
    }

    /**
     * 仅校验导入列表的合法性
     */
    @Override
    public Result<String> validateQuestions(List<QuestionImportVo> questions) {
        String error = validateList(questions);
        if (error != null) {
            return Result.error(error);
        }
        log.info("导入校验通过，记录数：{}", questions == null ? 0 : questions.size());
        return Result.success("验证通过");
    }

    /**
     * AI生成题目预留：当前未接入大模型
     */
    @Override
    public Result<List<QuestionImportVo>> generateQuestionsByAi(AiGenerateRequestVo request) {
        log.info("收到AI生成题目请求，topic={}，count={}，types={}", request.getTopic(), request.getCount(), request.getTypes());
        // 预留AI接入点：当前仅返回未实现提示
        return Result.error("AI生成题目暂未接入，请稍后再试");
    }

    //定义进行题目访问次数增长的方法
    //异步方法
    private void incrementQuestion(Long questionId){
        Double score = redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY,questionId,1);
        log.info("完成{}题目分数累计，累计后分数为：{}",questionId,score);
    }

    /**
     * 列表级验证：检查空列表并逐条调用单题校验
     * @param questions 导入题目列表
     * @return 首个错误消息，全部通过时返回null
     */
    private String validateList(List<QuestionImportVo> questions) {
        if (questions == null || questions.isEmpty()) {
            return "未获取到题目数据，请检查文件内容";
        }
        for (int i = 0; i < questions.size(); i++) {
            String error = validateSingleQuestion(questions.get(i), i + 1);
            if (error != null) {
                log.warn("导入校验失败，第{}题错误：{}", i + 1, error);
                return error;
            }
        }
        return null;
    }

    /**
     * 验证单题数据合法性（类型、选项/答案完整性）
     * @param question 单题数据
     * @param index 序号（用于提示）
     * @return 错误消息，校验通过返回null
     */
    private String validateSingleQuestion(QuestionImportVo question, int index) {
        if (question.getTitle() == null || question.getTitle().trim().isEmpty()) {
            return String.format("第%d题：题目内容不能为空", index);
        }
        String type = question.getType() == null ? null : question.getType().trim().toUpperCase();
        if (type == null || type.isEmpty()) {
            return String.format("第%d题：题目类型不能为空", index);
        }
        if (!"CHOICE".equals(type) && !"JUDGE".equals(type) && !"TEXT".equals(type)) {
            return String.format("第%d题：题目类型必须是CHOICE、JUDGE或TEXT", index);
        }
        if ("CHOICE".equals(type)) {
            if (question.getChoices() == null || question.getChoices().isEmpty()) {
                return String.format("第%d题：选择题必须有选项", index);
            }
            if (question.getChoices().size() < 2) {
                return String.format("第%d题：选择题至少需要2个选项", index);
            }
            boolean hasCorrectAnswer = question.getChoices().stream()
                    .anyMatch(choice -> choice.getIsCorrect() != null && choice.getIsCorrect());
            if (!hasCorrectAnswer) {
                return String.format("第%d题：选择题必须有正确答案", index);
            }
        } else {
            if (question.getAnswer() == null || question.getAnswer().trim().isEmpty()) {
                return String.format("第%d题：%s必须有答案", index, "JUDGE".equals(type) ? "判断题" : "简答题");
            }
        }
        return null;
    }

    /**
     * 导入DTO转实体，统一落库入口
     */
    private Question convertToQuestion(QuestionImportVo vo) {
        // 基础字段直接拷贝，忽略后续需要单独处理的字段
        Question question = new Question();
        BeanUtil.copyProperties(vo, question, "choices", "answer", "keywords");
        question.setType(vo.getType() == null ? null : vo.getType().toUpperCase());

        if ("CHOICE".equalsIgnoreCase(question.getType())) {
            List<QuestionChoice> choices = new ArrayList<>();
            List<QuestionImportVo.ChoiceImportDto> choiceVos = vo.getChoices();
            if (choiceVos != null) {
                for (int i = 0; i < choiceVos.size(); i++) {
                    QuestionImportVo.ChoiceImportDto cvo = choiceVos.get(i);
                    QuestionChoice choice = new QuestionChoice();
                    choice.setContent(cvo.getContent());
                    choice.setIsCorrect(cvo.getIsCorrect());
                    choice.setSort(cvo.getSort() == null ? i : cvo.getSort());
                    choices.add(choice);
                }
            }
            question.setChoices(choices);
        } else {
            QuestionAnswer answer = new QuestionAnswer();
            answer.setAnswer(vo.getAnswer());
            answer.setKeywords(vo.getKeywords());
            question.setAnswer(answer);
        }
        return question;
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

        // 1) 答案：一次性批量查询，避免 N+1
        Map<Long, QuestionAnswer> answerMap = questionAnswerMapper.selectList(
                        new LambdaQueryWrapper<QuestionAnswer>().in(QuestionAnswer::getQuestionId, questionIds))
                .stream()
                .collect(Collectors.toMap(QuestionAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        // 2) 选项：一次性批量查询，按题目分组
        Map<Long, List<QuestionChoice>> choiceMap = questionChoiceMapper.selectList(
                        new LambdaQueryWrapper<QuestionChoice>()
                                .in(QuestionChoice::getQuestionId, questionIds)
                                .orderByAsc(QuestionChoice::getSort))
                .stream()
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));

        // 3) 分类：批量查询后映射
        Map<Long, Category> categoryMap = categoryIds.isEmpty()
                ? Map.of()
                : categoryMapper.selectList(new LambdaQueryWrapper<Category>().in(Category::getId, categoryIds))
                .stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        // 4) 回填
        questions.forEach(q -> {
            QuestionAnswer answer = answerMap.get(q.getId());
            if (answer != null) {
                q.setAnswer(answer);
            }
            q.setChoices(choiceMap.getOrDefault(q.getId(), new ArrayList<>()));
            if (q.getCategoryId() != null) {
                q.setCategory(categoryMap.get(q.getCategoryId()));
            }
        });
    }

    /**
     * 解析并校验题目类型，使用枚举保证合法性
     */
    private QuestionType resolveType(String type) {
        QuestionType questionType = QuestionType.fromString(type);
        if (questionType == null) {
            throw new RuntimeException("不支持的题目类型：" + type);
        }
        return questionType;
    }

    /**
     * 选择题保存/更新统一处理
     * 1. 校验选项列表非空且每个内容非空
     * 2. 多选题正确选项数 >=2，单选题正确选项数 =1
     * 3. 依据 sort/下标生成标准答案字母串（A/B/C...），多选使用英文逗号拼接
     */
    private void handleChoiceForSave(Question question, QuestionAnswer answer) {
        List<QuestionChoice> choices = question.getChoices();
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("选择题至少需要配置一个选项");
        }

        int correctCount = 0;
        StringBuilder correctAnswer = new StringBuilder();
        for (int i = 0; i < choices.size(); i++) {
            QuestionChoice choice = choices.get(i);
            String content = choice.getContent() == null ? "" : choice.getContent().trim();
            if (content.isEmpty()) {
                throw new RuntimeException("选择题选项内容不能为空");
            }
            // 如果前端未传排序，则按照当前下标保证顺序稳定
            int sort = choice.getSort() == null ? i : choice.getSort();
            choice.setSort(sort);
            choice.setQuestionId(question.getId());
            // 更新场景下避免主键冲突，依赖数据库自增
            choice.setId(null);
            choice.setCreateTime(null);

            questionChoiceMapper.insert(choice);

            if (Boolean.TRUE.equals(choice.getIsCorrect())) {
                correctCount++;
                if (!correctAnswer.isEmpty()) {
                    correctAnswer.append(",");
                }
                correctAnswer.append((char) ('A' + sort));
            }
        }

        boolean isMulti = Boolean.TRUE.equals(question.getMulti());
        if (isMulti && correctCount < 2) {
            throw new RuntimeException("多选题必须至少选择两个正确选项");
        }
        if (!isMulti && correctCount != 1) {
            throw new RuntimeException("单选题必须且只能有一个正确选项");
        }

        answer.setAnswer(correctAnswer.toString());
    }




}
