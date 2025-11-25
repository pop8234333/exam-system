package com.zmh.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class BaseEntity implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "创建时间")
    private Date createTime;

    /*
    通常情况下接口响应的Json对象中并不需要update_time、is_deleted等字段，
    这时只需在实体类中的相应字段添加@JsonIgnore注解，该字段就会在序列化时被忽略。
     */
    @JsonIgnore
    @Schema(description = "修改时间")
    private Date updateTime;

    @Schema(description = "逻辑删除")
    @TableField("is_deleted")
    @TableLogic
    @JsonIgnore
    private Byte isDeleted;

}