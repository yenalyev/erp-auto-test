# Immutable ERP test artifact. Runtime configuration and secrets are injected by the runner.
ARG TEST_BASE_IMAGE=mcr.microsoft.com/playwright/java:v1.50.0-noble
FROM ${TEST_BASE_IMAGE}

ARG MAVEN_VERSION=3.9.9
ARG VCS_REF=local
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"
ENV MAVEN_OPTS="-Dfile.encoding=UTF-8"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL --connect-timeout 15 --max-time 120 --retry 2 \
       "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
       -o /tmp/apache-maven.tar.gz \
    && tar -xzf /tmp/apache-maven.tar.gz -C /opt \
    && rm /tmp/apache-maven.tar.gz \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}" "${MAVEN_HOME}" \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/erp-auto-test

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline -DskipTests

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress test-compile -DskipTests

LABEL org.opencontainers.image.title="erp-auto-test" \
      org.opencontainers.image.revision="${VCS_REF}"

ENTRYPOINT ["mvn"]
CMD ["test", "--batch-mode", "--no-transfer-progress"]
