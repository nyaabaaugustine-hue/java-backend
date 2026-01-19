# Use OpenJDK 11 as the base image
FROM openjdk:11-jre-slim

# Install Gradle
RUN apt-get update && apt-get install -y curl unzip && rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /app

# Copy the Gradle wrapper and build files
COPY gradle ./gradle/
COPY gradlew .
COPY settings.gradle .
COPY build.gradle .

# Make gradlew executable
RUN chmod +x ./gradlew

# Copy source code and resources
COPY src ./src/

# Build the application using shadowJar task to create an executable JAR
RUN ./gradlew shadowJar -xmx512m

# Expose the port the app runs on (default is 8088 for dashboard)
EXPOSE 8088

# Run the application
CMD ["java", "-cp", "build/libs/driber-all.jar", "Main"]