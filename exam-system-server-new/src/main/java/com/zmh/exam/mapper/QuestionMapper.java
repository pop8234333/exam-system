package com.zmh.exam.mapper;


import com.zmh.exam.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Map;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 聚合统计：按分类统计题目数量，返回分类ID与数量的键值对列表
     */
    List<Map<String, Long>> countByCategory();

}
