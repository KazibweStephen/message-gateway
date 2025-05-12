# Build stage
FROM openjdk:11 AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean build

# Run stage
FROM openjdk:11
EXPOSE 9191

COPY --from=build /app/build/libs/message-gateway.jar .
CMD java -jar message-gateway.jar