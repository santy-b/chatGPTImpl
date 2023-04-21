package com.example.chatGPTImpl;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableEncryptableProperties
public class AppConfig {
    @Value("${model}")
    private String model;

    @Value("${username}")
    private String username;

    @Value("${password}")
    private String password;

    @Bean
    public Optimizer optimizer() {
        return new Optimizer(model, username, password);
    }
}
