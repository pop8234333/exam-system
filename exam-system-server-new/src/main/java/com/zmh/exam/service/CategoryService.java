package com.zmh.exam.service;

import com.zmh.exam.entity.Category;

import java.util.List;

public interface CategoryService {


    List<Category> getAllCategories();

    List<Category> getCategoryTree();
}