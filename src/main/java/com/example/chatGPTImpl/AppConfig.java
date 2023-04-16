package com.example.chatGPTImpl;


import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableEncryptableProperties
public class AppConfig {

    @Value("${api.endpoint}")
    private String apiEndpoint;

    @Value("${api.key}")
    private String apiKey;

    @Value("${model}")
    private String model;

    @Bean
    public Optimizer optimizer() {
        return new Optimizer(apiEndpoint, apiKey, model);
    }
}
