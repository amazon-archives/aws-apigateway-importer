FROM maven:3-jdk-8

LABEL Description="Tools to work with Amazon API Gateway, Swagger, and RAML"

COPY docker-entrypoint.sh /
COPY pom.xml /app/
COPY src /app/src/
COPY tst /app/tst/

WORKDIR /app

RUN mvn assembly:assembly \
    && mv target/aws-apigateway-*-jar-with-dependencies.jar /aws-apigateway-importer.jar \
    && mvn clean

VOLUME ["/root/.aws"]
VOLUME ["/data"]

WORKDIR /data

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["--help"]
