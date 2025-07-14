package com.example.news_retrieval_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NewsRetrievalSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(NewsRetrievalSystemApplication.class, args);
	}

}
