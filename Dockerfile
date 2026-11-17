FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY food-safety-backend/pom.xml food-safety-backend/pom.xml
COPY food-safety-backend/src food-safety-backend/src
COPY index.html signin.html createaccount.html home.html api-config.js auth.js ./
COPY assets ./assets

RUN mvn -B -f food-safety-backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/food-safety-backend/target/food-safety-backend-1.0.0.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=20"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
