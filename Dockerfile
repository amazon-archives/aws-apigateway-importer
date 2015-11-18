FROM maven:3-jdk-8
MAINTAINER Soenke Ruempler <soenke@ruempler.eu>

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

ADD . /usr/src/app

RUN mvn -e assembly:assembly

ENTRYPOINT java -jar target/aws-apigateway-importer-1.0.2-SNAPSHOT-jar-with-dependencies.jar
