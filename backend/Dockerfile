# ============== Stage 1: Maven Build ==============
FROM maven:3.9-eclipse-temurin-17 AS builder

LABEL maintainer="nestcart-project"
LABEL stage=builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B -q \
    && ls -la target/

# ============== Stage 2: Runtime ==============
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="nestcart-project"
LABEL description="NestCart Backend - SpringBoot with Actuator & Prometheus"

RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

WORKDIR /app

COPY --from=builder /app/target/*.jar /app/app.jar

RUN addgroup -S spring && adduser -S spring -G spring \
    && chown -R spring:spring /app

USER spring

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+ExitOnOutOfMemoryError \
    -Dfile.encoding=UTF-8"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
