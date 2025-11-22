package com.vitor.entomodata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EntomodataApplication {

	public static void main(String[] args) {
		SpringApplication.run(EntomodataApplication.class, args);
	}
}
