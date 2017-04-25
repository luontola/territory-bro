FROM java:8-jre-alpine

RUN adduser -S -h /app app

EXPOSE 8080

ENV PORT=8080 \
    DATABASE_URL=jdbc:postgresql://localhost/territorybro?user=territorybro&password=territorybro

WORKDIR /app

USER app

# TODO: optimize memory use, should run in under 100 MB heap
CMD ["java", "-Xmx500m", "-XX:MaxMetaspaceSize=64m", "-jar", "territory-bro.jar"]

COPY target/territory-bro.jar /app/territory-bro.jar
