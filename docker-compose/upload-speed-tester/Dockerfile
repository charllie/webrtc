from debian:jessie

RUN apt-get update
RUN apt-get install -y nodejs npm git
RUN ln -s /usr/bin/nodejs /usr/bin/node
RUN git clone -b dev-sip https://github.com/charllie/webrtc
WORKDIR "/webrtc/upload-speed-tester"

RUN echo "#!/bin/bash" > start.sh
RUN echo "git pull" >> start.sh
RUN echo "npm install" >> start.sh
RUN echo "node server.js" >> start.sh

RUN chmod +x start.sh

ENTRYPOINT ["./start.sh"]
EXPOSE 8081
