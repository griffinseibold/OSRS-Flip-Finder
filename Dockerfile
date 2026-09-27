FROM node:24-slim AS frontend

WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend ./
RUN npm test && npm run build

FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY backend/src ./src
# Spring Boot serves the built frontend as static content alongside the API.
COPY --from=frontend /workspace/dist ./src/main/resources/static
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:25-jre-noble

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid 10001 --no-create-home --home-dir /app app \
    && mkdir --parents /data \
    && chown 10001:10001 /data

WORKDIR /app

COPY --from=build --chown=10001:10001 \
    /workspace/target/flipfinder-0.0.1-SNAPSHOT.jar ./flipfinder.jar

ENV SERVER_PORT=8080

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/flipfinder.jar"]
