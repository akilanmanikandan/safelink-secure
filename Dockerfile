FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY pom.xml ./
COPY src ./src
COPY config.json ./
COPY data ./data
COPY encrypted_files ./encrypted_files

RUN mvn clean compile package

EXPOSE 8080

CMD ["mvn", "spring-boot:run"]
