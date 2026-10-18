package com.atifah.community;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 社区树洞服务启动类。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@EnableDiscoveryClient
@MapperScan("com.atifah.community.mapper")
@SpringBootApplication
public class CommunityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }
}
