FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn -q -DskipTests dependency:resolve

COPY . .

RUN mvn -q -DskipTests package

FROM mcr.microsoft.com/playwright/java:latest

WORKDIR /app

COPY --from=builder /app/target/AutoSignupBot-1.0-SNAPSHOT.jar app.jar

COPY config ./config
COPY tokens ./tokens

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]