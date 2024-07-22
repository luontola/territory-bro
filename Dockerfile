FROM eclipse-temurin:17-jre-focal

EXPOSE 8080
ENV PORT=8080 \
    DATABASE_URL="jdbc:postgresql://localhost/territorybro?user=territorybro&password=territorybro"

# the user should have no write permissions, even to its home
RUN adduser --no-create-home --home /app app && \
    mkdir /app
WORKDIR /app

COPY target/uberjar/territory-bro.jar /app/

# prepare AppCDS shared archive
COPY target/uberjar/classes.list /app/
RUN java -Xshare:dump \
        -XX:SharedClassListFile=classes.list \
        -XX:SharedArchiveFile=classes.jsa \
        --class-path territory-bro.jar && \
    rm classes.list

USER app
ENTRYPOINT ["java", \
            "-Xshare:on", "-XX:SharedArchiveFile=classes.jsa", \
            "-XX:InitialRAMPercentage=70", "-XX:MaxRAMPercentage=70", \
            "-XX:+PrintCommandLineFlags", \
            "-jar", "territory-bro.jar"]

ARG GIT_COMMIT
ENV GIT_COMMIT=$GIT_COMMIT

ARG BUILD_TIMESTAMP
ENV BUILD_TIMESTAMP=$BUILD_TIMESTAMP

# The version number is not known at CI build time, but it's included
# here as a reminder of its existence, and for use during a local build.
ARG RELEASE_VERSION
ENV RELEASE_VERSION=$RELEASE_VERSION
