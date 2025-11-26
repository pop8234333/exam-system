package com.zmh.exam.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zmh.exam.entity.Category;
import com.zmh.exam.entity.Question;
import com.zmh.exam.mapper.CategoryMapper;
import com.zmh.exam.mapper.QuestionMapper;
import com.zmh.exam.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;


    /**
     * 获取包含题目数量的信息
     * @return
     */
    @Override
    public List<Category> getAllCategories() {
        // 1) 拉取全部分类（按 sort 升序，保持前端展示顺序）
        List<Category> categoryList = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort));
        if (categoryList.isEmpty()) {
            return categoryList;
        }

        // 2) Mapper 层聚合统计各分类下题目数量，避免在 Service 层写 SQL
        //第一步得到的是{<categoryId,1>,<cnt,1>}List<Map>
        Map<Long, Long> countMap = questionMapper.countByCategory()
                .stream()
                .collect(Collectors.toMap(
                        m -> m.get("categoryId"),
                        m -> m.get("cnt")
                ));

        // 3) 回填数量字段，没题目的分类默认 0
        categoryList.forEach(c -> c.setCount(countMap.getOrDefault(c.getId(), 0L)));

        return categoryList;

    }

    @Override
    public List<Category> getCategoryTree() {
        List<Category> categories = getAllCategories();//1.获取包含题目数量的分类信息

        // 2. 使用Stream API按parentId进行分组，得到 Map<parentId, List<children>>
        Map<Long, List<Category>> childrenMap = categories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        /*
            stream()：把 List<Category> 转成 Stream 流，开启流式操作。
            Collectors.groupingBy：按指定规则分组，这里用方法引用 Category::getParentId ，提取分类的 parentId 作为分组 key，value 是对应 parentId 的分类列表，快速构建 父 ID - 子分类列表 映射。
         */
        // 3. 遍历所有分类，为它们设置children属性，并递归地累加题目数量
        categories.forEach(category -> {
            // 从Map中找到当前分类的所有子分类,根据父类分类id找到对应的所有子分类列表
            List<Category> children = childrenMap.getOrDefault(category.getId(), new ArrayList<>());
            //将找到的子分类列表赋值给当前分类
            category.setChildren(children);

            // 汇总子分类的题目数量到父分类
            long childrenQuestionCount = children.stream()
                    .mapToLong(c -> c.getCount() != null ? c.getCount() : 0L)
                    .sum();
            /*
                forEach：遍历每个分类，对单个分类做处理，类似增强 for 循环，但结合 Stream 更灵活。
                getOrDefault：从分组好的 childrenMap 取当前分类的子分类，无对应值时给默认空列表，避免空指针。
                嵌套 stream().mapToLong().sum()：先转成 LongStream ，通过 mapToLong 处理 count （空值转 0 ），再用 sum 汇总子分类题目数，结合自身题目数，设置到当前分类，完成递归汇总逻辑。
             */

            long selfQuestionCount = category.getCount() != null ? category.getCount() : 0L;
            // 父分类的总数 = 自身的题目数 + 所有子分类的题目数总和
            category.setCount(selfQuestionCount + childrenQuestionCount);
        });

        // 4. 最后，筛选出所有顶级分类（parentId为0），它们是树的根节点
        /*
            filter：按条件（parentId == 0 ）过滤分类，只保留顶级分类。
            collect(Collectors.toList())：把过滤后的 Stream 流转为 List ，作为分类树的根节点集合返回。
         */
        List<Category> categoryTree=categories.stream()
                .filter(c -> c.getParentId() == 0)
                .collect(Collectors.toList());
        log.info("查询类别树状结构集合：{}",categoryTree);

        return categoryTree;
    }

    @Override
    public void saveCategory(Category category) {
        //判断当前父类下面是否有重复的名字
        long count = count(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, category.getParentId())
                .eq(Category::getName, category.getName()));
        if (count > 0) {
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次保存失败！"
                    .formatted(parent.getName(),category.getName()));
        }else  {
            save(category);
            log.info("添加成功");
        }

    }

    @Override
    public void updateCategory(Category category) {

        //判断当前父类下面是否有重复的名字,并排除修改本身的数据
        boolean exists = exists(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, category.getParentId())
                .ne(Category::getId, category.getId())
                .eq(Category::getName, category.getName()));
        if (exists) {
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次更新失败！"
                    .formatted(parent.getName(),category.getName()));
        }else {
            updateById(category);
        }

    }

    @Override
    public void removeCategory(Long id) {
        //检查是否是一级父题目,即parent_id=0
        if(getById(id).getParentId() ==0){
            throw new RuntimeException("不能删除一级父题目");
        }

        //检查该分类下是否有题目

        boolean exists = questionMapper.exists(new LambdaQueryWrapper<Question>()
                .eq(Question::getCategoryId, id));
        if (exists) {
            throw new RuntimeException("该分类下还有题目,不能删除");
        }

        removeById(id);
    }
}
