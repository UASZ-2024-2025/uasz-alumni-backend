# syntax=docker/dockerfile:1
# Contexte de build = RACINE du monorepo (il faut spis/repo).
#   docker build -f docker/service.Dockerfile --build-arg SERVICE=user-service -t user-service:dev .
ARG SERVICE

# ---------- Etape 1 : build ----------
FROM eclipse-temurin:21-jdk-jammy AS build
ARG SERVICE

# Depot Maven des SPIs, meme chemin relatif que dans le monorepo (../spis/repo)
WORKDIR /workspace
COPY spis/repo/ spis/repo/

WORKDIR /workspace/${SERVICE}
COPY ${SERVICE}/.mvn/ .mvn/
COPY ${SERVICE}/mvnw ${SERVICE}/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY ${SERVICE}/src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && cp target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Etape 2 : image d'execution ----------
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
WORKDIR /app

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

# Couches ordonnees du moins au plus volatile -> meilleur cache de build/push
COPY --from=build /workspace/${SERVICE}/extracted/dependencies/ ./
COPY --from=build /workspace/${SERVICE}/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/${SERVICE}/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/${SERVICE}/extracted/application/ ./

USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "application.jar"]
