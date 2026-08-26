# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY common-lib/pom.xml common-lib/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn dependency:go-offline -pl notification-service -am -q

COPY common-lib/src common-lib/src
COPY notification-service/src notification-service/src
RUN mvn verify -pl notification-service -am -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system banking && useradd --system --gid banking banking

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

USER banking

EXPOSE 8084

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
