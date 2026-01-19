# Use OpenJDK 11 as the base image
FROM eclipse-temurin:11-jre

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
CMD ["java", "-jar", "build/libs/driber-all.jar"]