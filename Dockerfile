FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring

COPY app.jar /app/app.jar

RUN chown spring:spring /app/app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]