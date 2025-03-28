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

# try out JEP 483: Ahead-of-Time Class Loading & Linking
# 	https://openjdk.org/jeps/483
ENTRYPOINT ["java", \
            "-XX:AOTMode=record", \
            "-XX:AOTConfiguration=warmup/app.aotconf", \
            "-XX:+PrintCommandLineFlags", \
            "-jar", "territory-bro.jar"]

FROM warmup AS build

COPY target/warmup/app.aotconf /app/warmup/

# FIXME: crashes with "Archive heap points to a static field that may hold a different value at runtime"
RUN java -XX:AOTMode=create \
         -XX:AOTConfiguration=warmup/app.aotconf \
         -XX:AOTCache=app.aot \
         -XX:+PrintCommandLineFlags \
         --class-path territory-bro.jar

USER app
ENTRYPOINT ["java", \
            "-XX:AOTMode=on", \
            "-XX:AOTCache=app.aot", \
            "-XX:MaxRAMPercentage=70", \
            "-XX:+PrintCommandLineFlags", \
            "-jar", "territory-bro.jar"]
