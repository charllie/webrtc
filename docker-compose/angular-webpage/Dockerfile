from debian:jessie

RUN apt-get update
RUN apt-get install -y nodejs npm git
RUN ln -s /usr/bin/nodejs /usr/bin/node
RUN npm install bower -g
RUN npm install http-server -g
RUN echo "dontusecache" && git clone -b dev-sip https://github.com/charllie/webrtc
WORKDIR "/webrtc/angular-webpage"
RUN echo "#!/bin/bash" > start.sh
RUN echo "git pull" >> start.sh
RUN echo "bower install --allow-root" >> start.sh
RUN echo "cp /conf/config.json ." >> start.sh
RUN echo "http-server -p 80" >> start.sh
RUN chmod +x start.sh
ENTRYPOINT ["./start.sh"]
EXPOSE 80