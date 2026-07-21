package com.consentradar.consentradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConsentradarApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsentradarApplication.class, args);
	}

}
