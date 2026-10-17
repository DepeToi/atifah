package com.microhoov.growth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@MapperScan("com.microhoov.growth.mapper")
@SpringBootApplication
public class GrowthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrowthServiceApplication.class, args);
    }
}
