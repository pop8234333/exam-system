package com.atguigu.exam.service;

import com.atguigu.exam.entity.Category;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface CategoryService extends IService<Category> {

    /**
     * 查询所有分类和分类的题目数量
     * @return 分类 + count
     */
    List<Category> getCategoryList();

    /**
     * 查询树状分类集合
     * @return 分类 + count + child
     */
    List<Category> getCategoryTreeList();

    /**
     * 进行分类新增
     * @param category
     */
    void saveCategory(Category category);

    /**
     * 进行分类更新
     * @param category
     */
    void updateCategory(Category category);

    /**
     * 删除分类信息
     * @param id
     */
    void removeCategoryById(Long id);
}