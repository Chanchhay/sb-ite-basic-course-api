FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew

# Resolve dependencies before the source is copied, so a code-only change
# reuses this layer instead of re-downloading Gradle and every artifact.
RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew clean build -x check -x test -Pproduction --no-daemon

RUN cp "$(ls -1 build/libs/*.jar | grep -v plain)" app.jar


FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/app.jar /app/app.jar

RUN chown spring:spring /app/app.jar

USER spring

EXPOSE 8080

# Without a percentage the JVM takes only a quarter of the container's memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# `exec` hands PID 1 to the JVM so SIGTERM reaches it and the graceful
# shutdown configured in application-prod.yaml actually runs.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
