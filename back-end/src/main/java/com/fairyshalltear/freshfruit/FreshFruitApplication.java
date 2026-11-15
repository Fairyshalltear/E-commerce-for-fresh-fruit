package com.fairyshalltear.freshfruit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FreshFruitApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreshFruitApplication.class, args);
    }
}
