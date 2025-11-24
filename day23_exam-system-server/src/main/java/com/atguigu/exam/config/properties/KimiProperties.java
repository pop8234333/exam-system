package com.atguigu.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * projectName: day23_exam-system-server
 *
 * @author: 赵伟风
 * description: 接收kimi调用的五个参数
 */
@ConfigurationProperties(prefix = "kimi.api")
@Data
public class KimiProperties {

    private String model;
    private String uri;
    private String apiKey;
    private Integer maxTokens;
    private Double temperature;
}
