package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.QuestionPageVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface QuestionMapper extends BaseMapper<Question> {


    /**
     * 查询题目中分类的数量！
     * @return Map<分类id，题目数量>
     * mybatis  @Param @MapKey @Select @Update @Insert @Delete
     */
    @Select("select category_id,count(*) ct from questions WHERE is_deleted = 0 " +
            " GROUP BY category_id ")
    List<Map<Long,Long>> selectCategoryCount();

    /**
     * 分页查询题目信息，第一步！一会要触发，根据题目id查询选项！！
     * @param page 分页对象
     * @param queryVo 自己实体类,封装的查询对象！
     * @return
     */
    IPage<Question> customPage(IPage page, @Param("queryVo") QuestionPageVo queryVo);

    Question customGetById(Long questionId);

    /**
     * 根据试卷id查询题目集合
     * @param paperId
     * @return
     */
    List<Question> customQueryQuestionListByPaperId(Long paperId);

} 