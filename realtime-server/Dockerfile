FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle ./common/
COPY web-server/build.gradle ./web-server/
COPY realtime-server/build.gradle ./realtime-server/
COPY load-client/build.gradle ./load-client/
RUN gradle --no-daemon :realtime-server:dependencies > /dev/null 2>&1 || true
COPY common ./common
COPY realtime-server ./realtime-server
RUN gradle --no-daemon :realtime-server:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/realtime-server/build/libs/realtime-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
