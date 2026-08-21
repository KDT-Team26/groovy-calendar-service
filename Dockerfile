# 빌드 컨텍스트는 이 레포 루트여야 한다(Gradle 멀티모듈이라 이 서비스만 떼어 빌드할 수 없고,
# libs/event-contract, libs/observability, libs/web-common, libs/security-common,
# libs/client-common을 함께 봐야 ':services:calendar-service:bootJar'가 성립한다).

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY libs libs
COPY services services
RUN chmod +x gradlew && ./gradlew :services:calendar-service:bootJar --no-daemon -x test

# --- Run stage ---
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

RUN groupadd --system groovy && useradd --system --gid groovy --no-create-home groovy

COPY --from=build /workspace/services/calendar-service/build/libs/*-SNAPSHOT.jar app.jar
RUN chown groovy:groovy app.jar

USER groovy
EXPOSE 8084

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
	CMD wget -q --spider http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
