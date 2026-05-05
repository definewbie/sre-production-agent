FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
COPY otel-agent.jar otel-agent.jar
EXPOSE 8083
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
