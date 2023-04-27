package com.example.chatGPTImpl;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testng.annotations.Test;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;

import static org.testng.AssertJUnit.*;


@SpringJUnitConfig
@ContextConfiguration(classes = {OptimizerTest.TestConfig.class})
@SpringBootTest
public class OptimizerTest {


	public class TestConfig {
		private String model;
		private String username;
		private String password;

		private String applicationPropertiesPath = "/Users/brian/Code/java/chatGPTImpl/src/main/resources/application.properties";

		{
			try {
				model = getProperty("model");
				username = getProperty("username");
				password = getProperty("password");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		private String getProperty(String property) throws IOException {
			Properties props = new Properties();
			Reader reader = new FileReader(applicationPropertiesPath);
			props.load(reader);
			return props.getProperty(property);
		}

//		public Optimizer optimizer() {
//			return new Optimizer(model, username, password);
//		}
	}

//	private Optimizer optimizer = new TestConfig().optimizer();

	@Test
	public void testProcessCode() throws Exception {

	}
}
