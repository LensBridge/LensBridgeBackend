# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -e -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -e -ntp -DskipTests package \
 && cp target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd -r -g 1001 lensbridge \
 && useradd -r -u 1001 -g lensbridge lensbridge \
 && chown -R lensbridge:lensbridge /app
COPY --from=build /build/app.jar /app/app.jar
USER lensbridge
EXPOSE 8085
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
