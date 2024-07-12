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
ENTRYPOINT ["java", "-Xshare:on", "-XX:SharedArchiveFile=classes.jsa", "-jar", "territory-bro.jar"]
