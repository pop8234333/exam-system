package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.Category;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper,Category> implements CategoryService {

    @Autowired
    private QuestionMapper questionMapper;

    @Override
    public List<Category> getCategoryList() {
        //步骤1：查询所有分类信息
        // 1. 获取所有分类的基础信息，并按sort字段排序
        List<Category> categoryList = list(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
        );
        //步骤2：完成分类分析的题目数量填充 [tree也要使用！]
        fillCategoryCount(categoryList);
        //步骤3:返回完整结果
        return categoryList;
    }

    @Override
    public List<Category> getCategoryTreeList() {
        //步骤1：查询所有分类信息
        List<Category> categoryList = list();
        //步骤2：完成分类分析的题目数量填充 [tree也要使用！]
        fillCategoryCount(categoryList);
        //3. 给所有分类根据parent_id进行分组  parent_id -> 子分类集合 （map）
        Map<Long, List<Category>> parentIdMap = categoryList.stream().collect(Collectors.groupingBy(Category::getParentId));
        //4. 筛选原来分类集合中 parent_id = 0
        List<Category> categoryListResult = categoryList.stream().filter(category -> category.getParentId() == 0).collect(Collectors.toList());
        //5. 对筛选后的集合进行循环 -》 1. map 找子分类集合 赋值 child 2. 父分类count = 自己count + 所有子分类的count
        categoryListResult.forEach(category -> {
            //赋值子分类
            List<Category> childCategoryList = parentIdMap.getOrDefault(category.getId(), new ArrayList<>());
            //第一版本
            //childCategoryList.sort((o1, o2) -> o1.getSort() - o2.getSort());
            //第二版本
            //childCategoryList.sort((o1, o2) -> Integer.compare(o1.getSort(), o2.getSort()));
            //第三版本
            childCategoryList.sort(Comparator.comparingInt(Category::getSort));

            category.setChildren(childCategoryList);
            //赋值count = 当前count + 子分类的count
            long childCount = childCategoryList.stream().mapToLong(Category::getCount).sum();
            category.setCount(category.getCount() + childCount);
        });
        //6. 返回分类集合 筛选 + 循环赋值
        return categoryListResult;
    }

    @Override
    public void saveCategory(Category category) {
        //1.判断同一个父类分类下不允许重名
        // parent_id = 传入 and name = 传入
        LambdaQueryWrapper<Category> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Category::getParentId, category.getParentId());
        lambdaQueryWrapper.eq(Category::getName,category.getName());
        long count = count(lambdaQueryWrapper);// count 查询存在的数量
        //知识点： 我们可以在自己的service获取自己的mapper -> CategoryMapper baseMapper = getBaseMapper();
        if (count > 0) {
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次添加失败！".formatted(parent.getName(),category.getName()));
        }
        //2.保存
        save(category);
    }

    @Override
    public void updateCategory(Category category) {
        //1.先校验  同一父分类下！ 可以跟自己的name重复，不能跟其他的子分类name重复！
        LambdaQueryWrapper<Category> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Category::getParentId, category.getParentId()); // 同一父分类下！
        lambdaQueryWrapper.ne(Category::getId, category.getId());
        lambdaQueryWrapper.eq(Category::getName, category.getName());
        CategoryMapper categoryMapper = getBaseMapper();
        boolean exists = categoryMapper.exists(lambdaQueryWrapper);
        if (exists) {
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次更新失败！".formatted(parent.getName(),category.getName()));
        }
        //2.再更新
        updateById(category);
    }


    @Override
    public void removeCategoryById(Long id) {
        //1.检查是否一级标题
        Category category = getById(id);
        if (category.getParentId() == 0){
            throw new RuntimeException("不能删除一级标题！");
        }
        //2.检查是否存在关联的题目
        LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Question::getCategoryId,id);
        long count = questionMapper.selectCount(lambdaQueryWrapper);
        if (count>0){
            throw new RuntimeException("当前的:%s分类，关联了%s道题目,无法删除！".formatted(category.getName(),count));
        }
        //3.以上不都不满足，删除即可【子关联数据，一并删除】
        removeById(id);
    }

    /**
     * 给与分类信息进行count填充
     *   1. 判断分类集合是否为empty [对一个集合 对一个数据要进行大量逻辑代码之前，尽量先判断！否则出现无用功！！]
     *   2. 查询所有分类和分类对应的题目数量 【mapper方法】
     *   3. 进行分类数据和count转化，map<categoryId,count>
     *   4. 给集合每个分类，查询count,并赋值即可
     * @param categoryList
     */
    private void fillCategoryCount(List<Category> categoryList){
        //1. 判断分类集合是否为empty [对一个集合 对一个数据要进行大量逻辑代码之前，尽量先判断！否则出现无用功！！]
        if (categoryList == null || categoryList.isEmpty()){
            throw new RuntimeException("查询分类集合为空！");
        }
        //2.查询所有分类和分类对应的题目数量 【mapper方法】
        //查询题目中分类的题目数量
        List<Map<Long, Long>> mapList = questionMapper.selectCategoryCount();
        //[{1,3},{2,3},{4,1},{5,0}] O-N O 1
        //              |
        // List<Map<Long, Long>> mapList -》 Map{1=3,2=3,4=1...}
        Map<Long, Long> resultCount = mapList.stream().collect(Collectors.toMap(m -> m.get("category_id"), m -> m.get("ct")));
        //3. 进行分类结果填充
        categoryList.forEach(category -> {
            category.setCount(resultCount.getOrDefault(category.getId(),0L));
        });
    }
}