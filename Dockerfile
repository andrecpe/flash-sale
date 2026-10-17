# Dockerfile único e parametrizado para os 3 serviços (build context = raiz do projeto).
# Compatível com Docker antigo (18.06): multi-stage SEM BuildKit (sem RUN --mount).
#
#   docker build --build-arg MODULE=checkout-service --build-arg SERVER_PORT=8081 -t flash-sale/checkout-service .
#
# ---------------------------------------------------------------------------------------------
# Estágio 1: build com Maven
# Compatibilidade com Docker antigo (18.06): o seccomp padrão bloqueia syscalls recentes e o script
# "mvn" falha ("ls: cannot access '/usr/bin/mvn': Operation not permitted"). Por isso o Maven é
# chamado diretamente pelo launcher Java (variável MVN) — funciona igual no Docker atual.
# ---------------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
ENV MVN="java -cp /usr/share/maven/boot/plexus-classworlds-*.jar -Dclassworlds.conf=/usr/share/maven/bin/m2.conf -Dmaven.home=/usr/share/maven -Dmaven.multiModuleProjectDirectory=/build org.codehaus.plexus.classworlds.launcher.Launcher"

# 1) Apenas os pom.xml: a camada de dependências fica em cache enquanto os poms não mudarem.
#    Esta camada é COMPARTILHADA pelos 3 serviços (ainda não depende do ARG MODULE).
COPY pom.xml ./
COPY api-gateway/pom.xml api-gateway/
COPY checkout-service/pom.xml checkout-service/
COPY order-processor-service/pom.xml order-processor-service/
RUN $MVN -B -q -Potel-agent dependency:go-offline

# 2) Código-fonte do módulo e empacotamento (testes rodam fora do Docker: mvnw test).
ARG MODULE
COPY ${MODULE}/src ${MODULE}/src
RUN $MVN -B -q -Potel-agent -pl ${MODULE} -am package -DskipTests

# ---------------------------------------------------------------------------------------------
# Estágio 2: runtime enxuto (JRE Alpine, usuário não-root, OpenTelemetry Java Agent)
# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
ARG MODULE
ARG SERVER_PORT=8080

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /build/${MODULE}/target/${MODULE}.jar /app/app.jar
COPY --from=build /build/${MODULE}/target/otel/opentelemetry-javaagent.jar /app/otel-agent.jar

USER app

ENV SERVER_PORT=${SERVER_PORT} \
    OTEL_SERVICE_NAME=${MODULE} \
    JAVA_TOOL_OPTIONS="-javaagent:/app/otel-agent.jar -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE ${SERVER_PORT}

# Health check do container: readiness do Actuator (wget do busybox; não precisa instalar curl)
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null "http://localhost:${SERVER_PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

