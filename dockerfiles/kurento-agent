from debian:jessie

RUN apt-get update
RUN apt-get install -y git maven openjdk-7-jdk
RUN echo "no cache" && git clone -b dev-sip https://github.com/charllie/webrtc
WORKDIR "/webrtc/kurento-agent"
RUN echo "#!/bin/bash" > start.sh
RUN echo "git pull" >> start.sh
RUN echo "mvn clean package" >> start.sh
RUN echo "mvn exec:java" >> start.sh
RUN chmod +x start.sh
ENTRYPOINT ["./start.sh"]
EXPOSE 8080