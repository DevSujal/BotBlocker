package com.assignment.backendengineering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendengineeringApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendengineeringApplication.class, args);
	}

}
