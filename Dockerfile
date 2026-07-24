FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
RUN ./gradlew clean build -x check -x test -Pproduction --no-daemon
RUN cp "$(ls -1 build/libs/*.jar | grep -v plain)" app.jar

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=dev

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
