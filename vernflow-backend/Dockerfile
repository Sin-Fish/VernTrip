FROM gradle:8.10.2-jdk21 AS build

WORKDIR /workspace
COPY build.gradle.kts ./
COPY src ./src
RUN gradle bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system verntrip \
    && useradd --system --gid verntrip --home-dir /app --shell /usr/sbin/nologin verntrip

WORKDIR /app
COPY --from=build --chown=verntrip:verntrip /workspace/build/libs/*.jar app.jar
RUN mkdir -p /app/storage/papers /app/storage/workspace \
    && chown -R verntrip:verntrip /app

USER verntrip

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
EXPOSE 8080
VOLUME ["/app/storage"]

HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=6 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
