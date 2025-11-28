package com.zmh.exam.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author zmh
 * @since 2025/11/25 16:46<br/>
 * -----------------------------<br/>
 * Project    : exam-system<br/>
 * Package    : com.zmh.exam.common<br/>
 * ClassName  : MybatisMetaObjectHandler<br/>
 * Description:  请填写类的描述
 */
@Component
public class MybatisMetaObjectHandler  implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictInsertFill(metaObject, "createTime", Date.class, now);
        this.strictInsertFill(metaObject, "updateTime", Date.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictUpdateFill(metaObject, "updateTime", Date.class, now);
    }
}
