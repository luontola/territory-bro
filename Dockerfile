FROM eclipse-temurin:24-jre AS warmup

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

# training run for AppCDS
ENTRYPOINT ["java", \
            "-XX:DumpLoadedClassList=warmup/classes.list", \
            "-XX:+PrintCommandLineFlags", \
            "-jar", "territory-bro.jar"]

FROM warmup AS build

# create an AppCDS shared archive
COPY target/warmup/classes.list /app/warmup/
RUN java -Xshare:dump \
        -XX:SharedClassListFile=warmup/classes.list \
        -XX:SharedArchiveFile=classes.jsa \
        --class-path territory-bro.jar

USER app
ENTRYPOINT ["java", \
            "-Xshare:on", "-XX:SharedArchiveFile=classes.jsa", \
            "-XX:MaxRAMPercentage=70", \
            "-XX:+PrintCommandLineFlags", \
            "-jar", "territory-bro.jar"]
