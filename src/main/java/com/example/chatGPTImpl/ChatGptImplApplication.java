package com.example.chatGPTImpl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class ChatGptImplApplication {
	private static Optimizer optimizer;

	public ChatGptImplApplication(AppConfig appConfig) {
		this.optimizer = appConfig.optimizer();
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ChatGptImplApplication.class, args);
		optimizer.optimizeCodeAndEmail("https://github.com/santy-b/snake/archive/refs/heads/main.zip" ,"sbrianfig@gmail.com");
	}

}
