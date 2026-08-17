FROM docker.io/eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM docker.io/eclipse-temurin:25-jre-alpine AS extract
WORKDIR /workspace
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM docker.io/eclipse-temurin:25-jre-alpine
RUN addgroup -S jmeet && adduser -S jmeet -G jmeet
WORKDIR /app

COPY --from=extract /workspace/extracted/dependencies/ ./
COPY --from=extract /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract /workspace/extracted/application/ ./

USER jmeet:jmeet
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
