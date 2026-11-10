# syntax=docker/dockerfile:1
# Build context = raiz do repositório (precisa do pom.xml pai)
#   docker build -f account-service/Dockerfile .
#   docker build -f account-service/Dockerfile --target test .   (roda os testes)

ARG SERVICE=account-service

# ---------- 1) build ----------
FROM maven:3.9-eclipse-temurin-25 AS build
ARG SERVICE
WORKDIR /workspace

# Camada de dependências: só muda quando algum pom.xml muda
COPY pom.xml .
COPY account-service/pom.xml account-service/
COPY transfer-service/pom.xml transfer-service/
COPY notification-service/pom.xml notification-service/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${SERVICE} -am dependency:go-offline

COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${SERVICE} -am package -DskipTests

# ---------- (opcional) testes ----------
FROM build AS test
ARG SERVICE
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl ${SERVICE} -am test

# ---------- 2) runtime ----------
FROM eclipse-temurin:25-jre AS runtime
ARG SERVICE
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/${SERVICE}/target/app.jar app.jar
USER app

EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dnet.bytebuddy.experimental=true"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
