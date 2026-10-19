# syntax=docker/dockerfile:1
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG JAVA_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
COPY . .
# T17 verifies tests separately; image builds need no database or credentials.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package \
    && mkdir /out \
    && set -- admin-system-start/target/*.jar \
    && test "$#" -eq 1 \
    && cp "$1" /out/app.jar

FROM ${JAVA_IMAGE} AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=10001:10001 /out/app.jar /app/app.jar
ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai -Djava.awt.headless=true -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=3 \
    CMD curl --fail --silent --max-time 4 http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
