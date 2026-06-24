package com.rrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RrsApplication {
    public static void main(String[] args) {
        SpringApplication.run(RrsApplication.class, args);
    }
}
