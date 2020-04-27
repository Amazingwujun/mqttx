# 本地快速部署
为了方便项目快速的跑起来，引进了docker来方便项目的部署

> 执行本地部署动作前，需要先下载docker

1. 通过IDE提供的打包功能将项目打包为target/*.jar
2. 进入 dockerfile 同级目录，执行 `docker build -t mqttx:v0.1 .`
3. 执行 docker-compose up

以上步骤适用于 windows10。