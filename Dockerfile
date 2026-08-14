FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/employee-management-1.0.0.jar app.jar
EXPOSE 8080
# Render's free instance has 512 MB. With no flags the JVM sizes itself optimistically and the
# kernel SIGKILLs the container (exit 137) under load or during startup. Budget, roughly:
#   heap 40% of 512 = ~205 MB, metaspace <=128 MB, code cache/threads/native ~95 MB  =>  ~430 MB,
# leaving ~80 MB headroom. SerialGC (rather than G1) keeps native GC overhead small at this size,
# and ExitOnOutOfMemoryError makes the app die cleanly so Render restarts it instead of thrashing.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=40","-XX:MaxMetaspaceSize=128m","-Xss512k","-XX:+UseSerialGC","-XX:+ExitOnOutOfMemoryError","-jar","app.jar"]
