package com.example.chatGPTImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class ChatGptImplApplication {
	@Autowired
	private static Optimizer optimizer;

	public ChatGptImplApplication(Optimizer optimizer) {
		this.optimizer = optimizer;
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ChatGptImplApplication.class, args);
		String filePath = "/Users/brian/Code/java/server/src/main/java/com/example/server/Test.java";
		String[] optimizedCodeResult = optimizer.processCode(filePath, optimizer.optimizePrompt());
		System.out.println(optimizer.toString(optimizedCodeResult));
	}

}
