package com.atifah.content;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 冥想内容服务启动类。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@EnableDiscoveryClient
@MapperScan("com.atifah.content.mapper")
@SpringBootApplication
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
