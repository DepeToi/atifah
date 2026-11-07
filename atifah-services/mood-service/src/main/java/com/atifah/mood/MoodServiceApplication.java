package com.atifah.mood;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 情绪打卡与日记服务启动类。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@EnableDiscoveryClient
@MapperScan("com.atifah.mood.mapper")
@SpringBootApplication
public class MoodServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoodServiceApplication.class, args);
    }
}
