package com.college.findit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinditApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinditApplication.class, args);
    }
}
