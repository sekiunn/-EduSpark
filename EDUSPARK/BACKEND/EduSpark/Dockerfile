# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="" \
    HOME=/home/eduspark

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      ffmpeg \
      fontconfig \
      fonts-noto-cjk \
      libreoffice-impress \
      libreoffice-writer \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r eduspark \
    && useradd -r -g eduspark -u 10001 -m -d /home/eduspark eduspark \
    && mkdir -p /app/temp /data/eduspark/knowledge \
    && chown -R eduspark:eduspark /app /data/eduspark /home/eduspark

WORKDIR /app
COPY --from=build /workspace/target/EduSpark-0.0.1-SNAPSHOT.jar /app/eduspark.jar
RUN chown eduspark:eduspark /app/eduspark.jar

USER eduspark
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/eduspark.jar"]
