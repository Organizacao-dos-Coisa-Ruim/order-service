# Build the application first: ./mvnw clean package
# Then build the image from this directory: docker build -t order-service .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/order-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
