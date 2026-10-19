# 编码规范（CODE）

> 遵循《阿里巴巴 Java 开发手册》。设计原理见 `docs/architecture.md`，模块继承见 `AGENTS.md` §3。
> 前端 TypeScript / Vue 规则见 `atifah-admin-web/frontend/AGENTS.md`。

## CODE-01 命名、常量与日志

- UpperCamel / lowerCamel / UPPER_SNAKE；禁止魔法值，常量登记到 `CommonConstants` / `CacheKeys` / `MqTopics` / `MqConsumerGroups` / `ErrorCode`。
- 禁止 `System.out`、`printStackTrace`、`console.log`；日志用 `@Slf4j` + 占位符：`log.info("userId={}", userId)`。
- 类与关键方法写中文 Javadoc；可序列化 DTO/实体带 `@Serial private static final long serialVersionUID = 1L;`。
- Java 21 语法可用（record、switch 表达式、虚拟线程），但不要为用而用。

## CODE-02 包与分层

- 包根 `com.atifah.<service>`；公共代码只在 `com.atifah.common.*`，业务代码不得写进公共包。
- 业务服务按 `controller` / `service` / `mapper` / `entity` / `dto` / `vo` / `config` 分包。
- Mapper 必须在 `com.atifah.<service>.mapper`，与启动类 `@MapperScan` 一致。
- 公共模块通过 `AutoConfiguration.imports` 自动装配，**不要也不要加 `@ComponentScan`**。
- Controller 只做参数校验与组装，不写业务逻辑；Service 不读 `HttpServletRequest`。

## CODE-03 启动类

- 三注解缺一不可：`@SpringBootApplication`、`@EnableDiscoveryClient`、`@MapperScan("com.atifah.<service>.mapper")`。
- 启动类内不写配置、不写业务逻辑。

```java
package com.atifah.mood;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@MapperScan("com.atifah.mood.mapper")
@SpringBootApplication
public class MoodServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoodServiceApplication.class, args);
    }
}
```

## CODE-04 依赖注入与 Lombok

- 构造注入：`private final` + `@RequiredArgsConstructor`；**禁止 `@Autowired` 字段注入**。
- Lombok 只用于样板代码：`@Getter` / `@Setter` / `@RequiredArgsConstructor` / `@Slf4j`。
- 不为工具方法引入新库；新增依赖先确认。

## CODE-05 统一响应与异常

- 对外统一 `Result<T>`（`code=0` 成功）；分页统一 `PageResult.of(records, total, page, size)`。
- 错误码只取 `ErrorCode`，禁止各服务硬编码数字，禁止自定义响应壳。
- 业务校验失败**抛 `BusinessException`**，由 `GlobalExceptionHandler` 转 `Result` 并映射 HTTP 状态；**禁止 Controller 自己 try-catch 包装**。
- 参数校验用 `@Valid` + Jakarta Validation，不手写 if 判空链。
- 新增通用错误码才登记 `ErrorCode`，并同步前端 `src/api/types.ts`。

## CODE-06 实体与 Mapper

- 实体继承 `BaseEntity`：雪花 `id`、`createdAt` / `updatedAt` 自动填充、`deleted` 逻辑删除；**禁止自增主键**。
- 表字段固定 `created_at` / `updated_at` / `deleted`；实体字段用 lowerCamel，依靠下划线转驼峰。
- Mapper 继承 `BaseMapper<T>`；复杂 SQL 才写 XML 到 `resources/mapper/`，必须 `#{}` 参数绑定。
- 写操作放 Service 并加事务；事务内不做远程调用、不发 MQ（用本地消息表/事务消息）。

## CODE-07 日志与 TraceId

- 日志格式已带 `%X{traceId}`，直接 `log.info(...)` 即可，不要手动拼接 TraceId。
- 敏感字段（日记正文、量表结果、手机号、密码）严禁打印到日志或控制台。
- 异常日志保留堆栈；业务异常不要重复打印。

## CODE-08 自检与诚实性

- 后端改动：`mvn -o -q -DskipTests compile`。
- 前端改动：在 `atifah-admin-web/frontend` 执行 `npm run type-check`。
- **不通过不得声称完成**；禁止删除测试、关闭校验、加 `@SuppressWarnings` / `@ts-ignore` 掩盖问题。
- 测试失败时在 PR 中贴出真实输出，不得只写“已通过”。
