package com.atifah.gateway;

import com.atifah.gateway.config.GatewayAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类，统一请求入口。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@EnableDiscoveryClient
@SpringBootApplication
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
