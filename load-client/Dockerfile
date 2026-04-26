FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle ./common/
COPY web-server/build.gradle ./web-server/
COPY realtime-server/build.gradle ./realtime-server/
COPY load-client/build.gradle ./load-client/
RUN gradle --no-daemon :load-client:dependencies > /dev/null 2>&1 || true
COPY common ./common
COPY load-client ./load-client
RUN gradle --no-daemon :load-client:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/load-client/build/libs/load-client.jar app.jar
EXPOSE 9100
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
