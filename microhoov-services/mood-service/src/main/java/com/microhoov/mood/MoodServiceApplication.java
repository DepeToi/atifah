package com.microhoov.mood;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@MapperScan("com.microhoov.mood.mapper")
@SpringBootApplication
public class MoodServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoodServiceApplication.class, args);
    }
}
