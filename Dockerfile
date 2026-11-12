# ---------- build ----------
FROM maven:3.9-eclipse-temurin-26-alpine AS build
WORKDIR /src

# Dependencies first, so this layer is reused when only the code changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# The tests need a database and ran before the push; the image build only packages.
RUN mvn -B -q -DskipTests package \
 && cp target/smart-farmer-procurement-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination /out

# ---------- run ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /out/dependencies/ ./
COPY --from=build /out/spring-boot-loader/ ./
COPY --from=build /out/snapshot-dependencies/ ./
COPY --from=build /out/application/ ./

# Never run as root
USER app

# Sized for a 512 MB / 0.1 CPU container (Render free plan)
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xms48m -Xmx224m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"

EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
