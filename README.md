WebRTC Conferencing Project [![Build Status](https://travis-ci.org/charllie/webrtc.svg?branch=master)](https://travis-ci.org/charllie/webrtc)
===========================

1. [Kurento Media Server](#kurento-media-server)
2. [Kurento Client](#kurento-client)
3. [Upload Speed Tester](#upload-speed-tester)
4. [Web Interface](#web-interface)
5. [nginx](#nginx)

[XIVO](http://www.xivo.io) may be needed to get a GUI for Asterisk and a [REST API](http://documentation.xivo.io/en/stable/api_sdk/rest_api/confd/lines.html#sip-lines) to get all line extensions for the Kurento Agent. You can also build your own REST API.

## Kurento Media Server

This project depends on [Kurento](http://www.kurento.org) so it has to be installed first.

1. Basic installation

 ```bash
 $ echo "deb http://ubuntu.kurento.org trusty kms6" | tee /etc/apt/sources.list.d/kurento.list
 $ wget -O - http://ubuntu.kurento.org/kurento.gpg.key | apt-key add -
 $ apt-get update
 $ apt-get install kurento-media-server-6.0
 ```

2. Installation via Docker

 ```bash
 $ docker build --file=kms6 -t kms:6 . 
 $ docker run --restart=always --net=host --name=kms6 kms:6
 ```


## Kurento Client

A Kurento Client is needed to process the information given by the users. In this project, the Kurento client mixes all user streams into 1 stream. It also enables 1 person in a room to do a screenshare.

1. Configuration (application.yml)

 ```yaml
 server:
    port: 8080
 kurento:
    # for instance: ws://localhost:8888/ws
    websocket: KMS_WEBSOCKET_URI
 xivo:
    # for instance: ws://localhost:8082/ws
    # please see /etc/asterisk/http.conf
    websocket: XIVO_WEBSOCKET_URI
    rest:
       # for instance: https://localhost:9486/1.1/lines_sip
       uri: XIVO_REST_API_URI
       login: XIVO_REST_API_LOGIN
       password: XIVO_REST_API_PASSWORD
 ```

2. Basic installation

 *Requirements: Java 7, Maven*

 ```bash
 $ mvn clean package
 $ mvn exec:java
 ```

3. Installation via Docker

 ```bash
 $ docker build --file=kurento-agent -t kurento-agent:6 . 
 $ docker run --restart=always --net=host --name=kurento-agent -v /root/conf:/webrtc/kurento-agent/config/ kurento-agent:6
 ```



## Upload Speed Tester
This little nodejs module enables to do an upload speed test on port 8081 to adapt the quality before going in a room.

1. Basic installation

 *Requirements: Node.js*

 ```bash
 $ npm install
 $ node server.js
 ```

2. Installation via Docker

 ```bash
 $ docker build --file=upload-speed-tester -t upload-speed-tester:1 . 
 $ docker run --restart=always --net=host --name=upload-speed-tester upload-speed-tester:1
 ```

## Web interface

1. Configuration (config.json)

 ```json
 {
     "__comment": {
      "instance_ws_uri": "ws://localhost:8080/groupcall",
      "instance_wss_uri": "wss://localhost/groupcall",
      "instance_upload_speed_tester_uri": "http://localhost:8081/upload"
     },
     "ws_uri": "KMS_WEBSOCKET_URI",
     "wss_uri": "KMS_SECURE_WEBSOCKET_URI",
     "upload_speed_tester_uri": "UPLOAD_SPEED_TESTER_URI"
 }
 ```

2. Basic installation

 *Requirements: Node.js, Bower, http-server*

 ```bash
 $ bower install --allow-root
 $ http-server -p 80
 ```

3. Installation via Docker

 ```bash
 $ docker build --file=angular-webpage -t angular-webpage:1 . 
 $ docker run --restart=always --net=host --name=angular-webpage -v /root/conf:/conf/ angular-webpage:1
 ```



### nginx
[nginx](http://nginx.org) may be needed to redirect ports and/or to enable SSL on the domain (which is mandatory to enable screensharing)

```nginx
server {
    listen 443 ssl default_server;

    server_name _;

    ssl on;
    ssl_certificate /etc/nginx/ssl/server.crt;
    ssl_certificate_key /etc/nginx/ssl/server.key;

    location / {
            proxy_pass http://127.0.0.1:80;
            proxy_redirect off;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    
    location /names {
            proxy_pass http://127.0.0.1:8080/names;
            proxy_redirect off;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /groupcall {
            proxy_pass http://127.0.0.1:8080/groupcall;
            proxy_redirect off;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            #fail_timeout 0;
            proxy_http_version 1.1;
            #proxy_read_timeout ;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
    }

    location /upload {
            proxy_pass http://127.0.0.1:8081/upload;
            proxy_redirect off;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```


## Docker-compose

An easy way to install the project is using [docker-compose](https://docs.docker.com/compose/install/).

1. First, install docker-compose
2. Edit the configuration files in docker-compose/conf/
3. Launch the project in the docker-compose folder:

```bash
$ docker-compose up 
```
