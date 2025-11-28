package com.zmh.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmh.exam.entity.PaperQuestion;
import com.zmh.exam.mapper.PaperQuestionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author zmh
 * @since 2025/11/28 18:48<br/>
 * -----------------------------<br/>
 * Project    : exam-system<br/>
 * Package    : com.zmh.exam<br/>
 * ClassName  : MyBatisPlusTests<br/>
 * Description:  请填写类的描述
 */
@SpringBootTest
public class MyBatisPlusTests {

    @Autowired
    PaperQuestionMapper paperQuestionMapper;

    @Test
    public void customDeletePaperQuestion() {
        boolean exists = paperQuestionMapper.exists(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getQuestionId, 8L));
        System.out.println("exists = " + exists);

    }
}
