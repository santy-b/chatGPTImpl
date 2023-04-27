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


	public static void main(String[] args) throws IOException {
		SpringApplication.run(ChatGptImplApplication.class, args);
		optimizer.optimizeCodeAndEmail("https://github.com/santy-b/Data-Structures/archive/refs/heads/main.zip" ,"sbrianfig@gmail.com");
	}

}