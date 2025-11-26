package com.zmh.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zmh.exam.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {


    List<Category> getAllCategories();

    List<Category> getCategoryTree();

    void saveCategory(Category category);

    void updateCategory(Category category);

    void removeCategory(Long id);
}