WebRTC Conferencing Project
=========================

 1. Kurento Media Server 6
 2. Kurento Agent
 3. Upload Speed tester
 4. Web interface
 5. nginx configuration
 6. docker-compose



To edit files in a Docker, first enter in the docker with this command:
> docker exec -i -t DOCKER_NAME /bin/bash

Kurento Media Server 6 (Port 8888)
------------------------------------------

This project depends on Kurento so it has to be installed first.

 1. Installation

```bash
docker build --file=kms6 -t kms:6 . 
docker run --restart=always --net=host --name=kms6 kms:6
```


Kurento Client (Port 8080)
--------------------------------
A Kurento Client is needed to process the information given by the users. In this project, the Kurento client mixes all user streams into 1 stream. It also enables 1 person in a room to do a screenshare.

1. Configuration (application.yml)

```yaml
server:
   port: 8080
kurento:
   websocket: KMS_WEBSOCKET_URI
xivo:
   websocket: XIVO_WEBSOCKET_URI
   rest:
      uri: XIVO_REST_API_URI + /1.1/lines_sip
```

2. Installation

```bash
docker build --file=kurento-agent -t kurento-agent:6 . 
docker run --restart=always --net=host --name=kurento-agent -v /root/conf:/webrtc/kurento-agent/config/ kurento-agent:6
```



Upload speed tester (Port 8081)
--------------------------------------
This little nodejs module enables to do an upload speed test to adapt the quality before going in a room. To install it:

```bash
docker build --file=upload-speed-tester -t upload-speed-tester:1 . 
docker run --restart=always --net=host --name=upload-speed-tester upload-speed-tester:1
```


Web interface (Port 80)
----------------------------

1. Configuration (config.json)

```json
{
    "ws_uri": KMS_WEBSOCKET_URI,
    "wss_uri": KMS_SECURE_WEBSOCKET_URI,
    "upload_speed_tester_uri": UPLOAD_SPEED_TESTER_URI
}
```

2. Installation

```bash
docker build --file=angular-webpage -t angular-webpage:1 . 
docker run --restart=always --net=host --name=angular-webpage -v /root/conf:/conf/ angular-webpage:1
```



nginx Config
---------------
nginx may be needed to redirect ports and/or to enable SSL on the domain (which is mandatory to enable screensharing)

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


docker-compose
---------------

An easy way to install the project is using docker-compose.

First install docker-compose : https://docs.docker.com/compose/install/

Installation in the docker-compose folder :

```bash
docker-compose up 
```
