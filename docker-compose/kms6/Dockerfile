from ubuntu:14.04.3

#RUN apt-get -y install linux-image-virtual linux-image-extra-virtual
#RUN apt-get update
#RUN apt-get install linux-image-$(uname -r)
#RUN ls /lib/modules
#RUN modprobe raw1394
#RUN chmod a+rw /dev/raw1394

RUN apt-get update
RUN apt-get -y install wget
RUN apt-get -y install software-properties-common python-software-properties
RUN echo "deb http://ubuntu.kurento.org trusty kms6" | tee /etc/apt/sources.list.d/kurento.list
#RUN apt-add-repository http://ubuntu.kurento.org
RUN wget -O - http://ubuntu.kurento.org/kurento.gpg.key | apt-key add -
RUN apt-get update
RUN apt-get install -y kurento-media-server-6.0

RUN echo "#!/bin/bash" > start.sh
RUN echo "apt-get update" >> start.sh
RUN echo "apt-get upgrade" >> start.sh
RUN echo "kurento-media-server" >> start.sh
RUN chmod +x start.sh
ENTRYPOINT ["./start.sh"]

EXPOSE 8888