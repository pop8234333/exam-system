package com.zmh.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author zmh
 * @since 2025/11/25 15:23<br/>
 * -----------------------------<br/>
 * Project    : exam-system<br/>
 * Package    : com.zmh.exam.config.properties<br/>
 * ClassName  : MinioProperties<br/>
 * Description:  请填写类的描述
 */
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucketName;
}
