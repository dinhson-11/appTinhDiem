package com.tinhdiem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TinhdiemApplication {

	public static void main(String[] args) {
		SpringApplication.run(TinhdiemApplication.class, args);
	}

}
