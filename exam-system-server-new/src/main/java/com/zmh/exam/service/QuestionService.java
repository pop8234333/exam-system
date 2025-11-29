package com.zmh.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmh.exam.entity.Question;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zmh.exam.common.Result;
import com.zmh.exam.vo.QuestionQueryVo;
import com.zmh.exam.vo.AiGenerateRequestVo;
import com.zmh.exam.vo.QuestionImportVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 题目业务服务接口 - 定义题目相关的业务逻辑
 * 
 * Spring Boot三层架构教学要点：
 * 1. Service层：业务逻辑层，位于Controller和Mapper之间
 * 2. 接口设计：定义业务方法规范，便于不同实现类的切换
 * 3. 继承IService：使用MyBatis Plus提供的通用服务接口，减少重复代码
 * 4. 事务管理：Service层是事务的边界，复杂业务操作应该加@Transactional
 * 5. 业务封装：将复杂的数据操作封装成有业务意义的方法
 *
 * MyBatis Plus教学：
 * - IService<T>：提供基础的CRUD方法（save、update、remove、list等）
 * - 自定义方法：在接口中定义特定业务需求的方法
 * - 实现类：继承ServiceImpl<Mapper, Entity>并实现自定义业务方法
 * 
 * 设计原则：
 * - 单一职责：专门处理题目相关的业务逻辑
 * - 开闭原则：通过接口定义，便于扩展新的实现
 * - 依赖倒置：Controller依赖接口而不是具体实现
 * 
 * @author 智能学习平台开发团队
 * @version 1.0
 */
public interface QuestionService extends IService<Question> {


    Page<Question> getPage(Page<Question> pageBean, QuestionQueryVo questionQueryVo);

    Question customDetailQuestion(Long id);
    /**
     * 进行题目信息保存
     * @param question
     */
    void customSaveQuestion(Question question);

    void customUpdateQuestion(Question question);

    void customDeleteQuestion(Long id);

    /**
     * 获取热门题目列表（不足时用最新题目补齐）
     * @param size 期望返回数量
     * @return 热门题目列表
     */
    List<Question> getPopularQuestions(Integer size);

    /**
     * 手动刷新热门题目缓存
     * @return 初始化的热门题目数量
     */
    int refreshPopularQuestions();

    /**
     * 生成导入模板
     * @return Excel模板字节数组
     */
    byte[] generateImportTemplate();

    /**
     * 预览导入内容（只解析不入库）
     * @param file Excel文件
     * @return 解析结果
     */
    Result<List<QuestionImportVo>> previewImport(MultipartFile file);

    /**
     * 直接从Excel导入
     * @param file Excel文件
     * @return 导入结果
     */
    Result<String> importFromExcel(MultipartFile file);

    /**
     * 批量导入（来自Excel或AI）
     * @param questions 题目列表
     * @return 导入结果
     */
    Result<String> importQuestions(List<QuestionImportVo> questions);

    /**
     * 校验导入列表
     * @param questions 题目列表
     * @return 校验结果
     */
    Result<String> validateQuestions(List<QuestionImportVo> questions);

    /**
     * AI生成题目（预览）
     * @param request 生成请求
     * @return 题目列表
     */
    Result<List<QuestionImportVo>> generateQuestionsByAi(AiGenerateRequestVo request);
}
