# Use Eclipse Temurin JDK 21 as the base image
FROM eclipse-temurin:21

# Set working directory inside the container
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy source code into the container
COPY . /app

# Run Maven build (clean & package)
RUN mvn clean package -DskipTests

# Expose application port
EXPOSE 8080

# Use ENTRYPOINT for better control of the command execution
ENTRYPOINT ["java", "-jar", "/app/target/marketplace-service-0.0.1-SNAPSHOT.jar"]

