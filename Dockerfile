# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
#
# Uses the project's own Gradle wrapper rather than a gradle:* base image, so
# the build runs the exact Gradle version pinned in gradle/wrapper - the image
# and a local ./gradlew build cannot drift apart.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first: these change rarely, so the dependency
# download below stays cached across source-only edits.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle ./
RUN sh ./gradlew --no-daemon dependencies --quiet || true

COPY src src

# Tests are not run here. They need a JVM per module and would be paid for on
# every deploy; correctness belongs in CI, where a red build stops the deploy.
RUN sh ./gradlew --no-daemon clean bootJar -x test \
    && mv build/libs/*.jar /workspace/app.jar

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Unprivileged: a process that only needs to read its own jar has no reason to
# be able to write to the image.
#
# /app/data is created and handed to that user because the `h2` profile keeps a
# file database at ./data/h2/appdb, relative to the working directory, and
# file.server-path writes uploads to ./data/uploads. Without this, a non-root
# process cannot create either path inside a root-owned WORKDIR: H2 fails to
# open the database, Hibernate then has no connection to read metadata from, and
# the whole thing surfaces as the misleading "Unable to determine Dialect
# without JDBC metadata" rather than a permission error.
#
# It costs nothing under the Postgres profiles, which never touch the directory.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app \
    && mkdir -p /app/data

COPY --from=build --chown=10001:app /workspace/app.jar app.jar
RUN chown -R 10001:app /app
USER 10001

# The data directory is container-local: recreated on every deploy and every
# restart, so anything written there is lost. Mount a volume here, or point
# SPRING_DATASOURCE_URL at a mounted path, if the data has to survive.
VOLUME ["/app/data"]

ENV SPRING_PROFILES_ACTIVE=prod

# Containers are memory-capped, and a percentage keeps the heap inside whatever
# the platform actually grants - on a small instance that is the difference
# between running and being OOM-killed. SerialGC because the alternatives cost
# threads and footprint that a single-core instance does not have to spare.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

# Documentation only: the platform decides the real port via PORT, which
# application-prod.yml binds.
EXPOSE 8088

# exec form via sh so $JAVA_OPTS expands, and exec so the JVM is PID 1 and
# receives SIGTERM directly - otherwise shutdown waits for a 10s kill timeout
# instead of draining requests gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
