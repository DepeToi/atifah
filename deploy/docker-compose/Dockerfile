# 通用后端镜像：只打包宿主机已构建的可执行 jar，不在容器内跑 Maven。
# 构建上下文必须是仓库根目录，由 docker-compose.app.yml 为每个服务注入参数；
# 构建前请先在仓库根目录执行 mvn clean package -DskipTests。
#
# 国内网络拉取基础镜像受限时可覆盖镜像源：
#   docker build --build-arg BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy ...
ARG BASE_IMAGE=eclipse-temurin:21-jre-jammy
FROM ${BASE_IMAGE}

# APP_JAR：相对仓库根目录的可执行 jar 路径，允许通配符但必须唯一命中（target 下的 *.jar.original 不会匹配）
# APP_PORT：应用监听端口，同时用于 EXPOSE 与健康检查
ARG APP_JAR
ARG APP_PORT=8080

ENV TZ=Asia/Shanghai \
    APP_PORT=${APP_PORT} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

# 非 root 运行；基础镜像为 Ubuntu jammy，已自带 bash
RUN groupadd --system atifah \
    && useradd --system --gid atifah --home-dir /app --shell /usr/sbin/nologin atifah \
    && mkdir -p /app \
    && chown atifah:atifah /app

WORKDIR /app
COPY --chown=atifah:atifah ${APP_JAR} /app/app.jar

USER atifah
EXPOSE ${APP_PORT}

# 仅探测端口可连通，不依赖 curl/wget；详细健康状态用宿主机 curl /actuator/health
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/$APP_PORT' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
