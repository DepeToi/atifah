package com.atifah.stats;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 运营统计服务启动类。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@EnableDiscoveryClient
@MapperScan("com.atifah.stats.mapper")
@SpringBootApplication
public class StatsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatsServiceApplication.class, args);
    }
}
