# 1단계: 빌드 환경 (Gradle이 내장된 공식 이미지 사용)
FROM gradle:8.7-jdk17-alpine AS builder
WORKDIR /app
COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# 2단계: 실행 환경 (경량 JRE 이미지로 용량 최소화)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx400m", "-Xms200m", "-jar", "app.jar"]
