# growth-service 独立镜像：构建上下文为本模块目录，只打包本模块 target/ 下预先构建好的可执行 jar。
#
# 前置：在仓库根目录执行 mvn clean package -DskipTests
# 构建（在模块目录执行）：docker build -t atifah/growth-service:local .
# 国内网络拉取基础镜像受限时可覆盖镜像源：
#   docker build --build-arg BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy \
#     -t atifah/growth-service:local .
# 统一编排见仓库 deploy/docker-compose/docker-compose.app.yml。

ARG BASE_IMAGE=eclipse-temurin:21-jre-jammy
FROM ${BASE_IMAGE}

# 应用监听端口，同时用于 EXPOSE 与健康检查
ARG APP_PORT=8088

ENV TZ=Asia/Shanghai \
    APP_PORT=${APP_PORT} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

# 非 root 运行；基础镜像为 Ubuntu jammy，已自带 bash
RUN groupadd --system atifah \
    && useradd --system --gid atifah --home-dir /app --shell /usr/sbin/nologin atifah \
    && mkdir -p /app \
    && chown atifah:atifah /app

WORKDIR /app
# *.jar.original 与 target 下的 class 不会匹配；若存在多个 *.jar，COPY 会失败，请先清理
COPY --chown=atifah:atifah target/*.jar /app/app.jar

USER atifah
EXPOSE ${APP_PORT}

# 仅探测端口可连通，不依赖 curl/wget；详细健康状态用宿主机 curl /actuator/health
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/$APP_PORT' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
