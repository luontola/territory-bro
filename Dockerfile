FROM eclipse-temurin:25-jre AS warmup

EXPOSE 8080
ENV PORT=8080 \
    DATABASE_URL="jdbc:postgresql://localhost/territorybro?user=territorybro&password=territorybro"

# the user should have no write permissions, even to its home
RUN useradd --no-create-home --home /app app && \
    mkdir /app
WORKDIR /app

COPY target/uberjar/territory-bro.jar /app/

ARG GIT_COMMIT
ENV GIT_COMMIT=$GIT_COMMIT

ARG BUILD_TIMESTAMP
ENV BUILD_TIMESTAMP=$BUILD_TIMESTAMP

# The version number is not known at CI build time, but it's included
# here as a reminder of its existence, and for use during a local build.
ARG RELEASE_VERSION
ENV RELEASE_VERSION=$RELEASE_VERSION

# Training run for the ahead-of-time cache.
# The warmup directory is mounted during the build process.
ENTRYPOINT ["java", \
            "-XX:AOTCacheOutput=warmup/app.aot", \
            "-XX:+PrintCommandLineFlags", \
            "-jar", "territory-bro.jar"]

FROM warmup AS build

COPY target/warmup/app.aot /app/app.aot

USER app
ENTRYPOINT ["java", \
            "-XX:AOTMode=on", \
            "-XX:AOTCache=app.aot", \
            "-XX:MaxRAMPercentage=70", \
            "-XX:+PrintCommandLineFlags", \
            "-jar", "territory-bro.jar"]
