package com.example.chatGPTImpl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;


@SpringBootApplication
public class ChatGptImplApplication {
	private static Optimizer optimizer;

	public ChatGptImplApplication(AppConfig appConfig) {
		this.optimizer = appConfig.optimizer();
	}


	public static void main(String[] args) {
		SpringApplication.run(ChatGptImplApplication.class, args);
		try {
			optimizer.optimizeCodeAndEmail("santy-b", "Data-Structures" , "main", "sbrianfig@gmail.com");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}