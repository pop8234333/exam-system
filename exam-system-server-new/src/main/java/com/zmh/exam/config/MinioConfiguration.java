package com.zmh.exam.config;

import com.zmh.exam.config.properties.MinioProperties;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zmh
 * @since 2025/11/25 15:25<br/>
 * -----------------------------<br/>
 * Project    : exam-system<br/>
 * Package    : com.zmh.exam.config<br/>
 * ClassName  : MinioConfiguration<br/>
 * Description:  请填写类的描述
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MinioProperties.class)
//作用：这个注解用于启用对指定配置属性类（这里是 MinioProperties.class ）的支持。如果没有这个注解，
// 即使定义了带有 @ConfigurationProperties 注解的配置类，
// Spring 也不会自动将配置文件中的属性绑定到该类的实例上。
public class MinioConfiguration {

    private final MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder().endpoint(properties.getEndpoint()).credentials(properties.getAccessKey(), properties.getSecretKey()).build();
    }
}
