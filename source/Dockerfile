# Use OpenJDK 11 as the base image
FROM eclipse-temurin:11-jdk

# Install Gradle
RUN apt-get update && apt-get install -y curl unzip && rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /app

# Copy the Gradle wrapper and build files
COPY source/gradle ./gradle/
COPY source/gradlew .
COPY source/settings.gradle .
COPY source/build.gradle .

# Make gradlew executable
RUN chmod +x ./gradlew

# Copy source code and resources
COPY source/src ./src/

# Build the application using shadowJar task to create an executable JAR
RUN ./gradlew shadowJar --no-daemon

# Expose the port the app runs on (default is 8088 for dashboard)
EXPOSE 8088

# Run the application
CMD ["java", "-jar", "build/libs/driber-all.jar"]