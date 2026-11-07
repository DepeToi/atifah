package com.atifah.growth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 签到积分与成长服务启动类。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@EnableDiscoveryClient
@MapperScan("com.atifah.growth.mapper")
@SpringBootApplication
public class GrowthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrowthServiceApplication.class, args);
    }
}
