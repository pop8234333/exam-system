package com.zmh.exam.config;

import com.zmh.exam.config.properties.KimiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(KimiProperties.class)
public class WebClientConfiguration {
    
    @Autowired
    private KimiProperties kimiProperties;

    @Bean
    public WebClient webClient() {
        System.out.println("kimiProperties = " + kimiProperties.getApiKey());
        WebClient webClient = WebClient.builder().
                baseUrl(kimiProperties.getUri()+"/chat/completions")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", "Bearer " + kimiProperties.getApiKey())
                .build();
        return webClient;
    }
}