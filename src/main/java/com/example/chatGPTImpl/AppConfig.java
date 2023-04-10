package com.example.chatGPTImpl;


import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.salt.RandomSaltGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableEncryptableProperties
public class AppConfig {
    private Environment env;

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
