# Multi-stage build para Render
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/install/taskdle-backend .

EXPOSE 8080

RUN chmod +x bin/taskdle-backend

CMD ["bin/taskdle-backend"]
