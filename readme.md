由于项目是由我一个人开发，所以我没有开分支。学习是没问题的，如果真正要进行功能测试、使用，那么需要注意版本的commit,有些commit是不可用版本，这些我是打了unavailable_version tag的。

项目运行的方式：
  1. 使用`springboot`推荐的启动方式 `java -jar app.jar`，使用 `mvn clean package` 打包，这种方式需要修改配置文件中 redis 地址和端口。
  2. 基于 `docker` 容器化部署，这个就比较简单，具体的步骤见 [容器化部署](#容器化部署)


# 容器化部署
为了方便项目快速的跑起来，引进了docker来方便项目的部署

> 执行本地部署动作前，需要先下载docker

1. 通过IDE提供的打包功能将项目打包为 target/*.jar
2. 进入 dockerfile 同级目录，执行 `docker build -t mqttx:v0.1 .`
3. 执行 docker-compose up

以上步骤适用于 windows10。