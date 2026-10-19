# syntax=docker/dockerfile:1

# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 先复制 POM，利用 Docker layer cache 缓存依赖
COPY pom.xml .
COPY nest-common/pom.xml nest-common/
COPY nest-pojo/pom.xml nest-pojo/
COPY nest-ai/pom.xml nest-ai/
COPY nest-rag/pom.xml nest-rag/
COPY nest-minio/pom.xml nest-minio/
COPY nest-chat/pom.xml nest-chat/
COPY nest-wallet/pom.xml nest-wallet/
COPY nest-order/pom.xml nest-order/
COPY nest-server/pom.xml nest-server/
RUN mvn -B -ntp -pl nest-server -am -DskipTests dependency:go-offline || true

COPY . .
RUN mvn -B -ntp -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system nest \
    && useradd --system --gid nest --home-dir /app nest

WORKDIR /app
COPY --from=build /workspace/nest-server/target/nest-server-*.jar app.jar

RUN mkdir -p /app/logs && chown -R nest:nest /app
USER nest

EXPOSE 8080 8081 9090

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
  CMD curl -fsS http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
