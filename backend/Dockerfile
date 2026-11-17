# Multi-stage build para Render
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle fatJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/taskdle-all.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
