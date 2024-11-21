FROM eclipse-temurin:17.0.13_11-jre-noble
WORKDIR .
COPY ./output/mock-server.jar ./mock-server.jar
CMD java -jar mock-server.jar