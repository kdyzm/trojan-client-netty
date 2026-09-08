FROM openjdk:8-jre-slim
MAINTAINER kdyzm

ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ADD target/trojan-client-*.jar /app.jar
# 非敏感运行期文件内置镜像；config.yml 含 trojan 服务端凭据，不入镜像——
# 运行时请挂载宿主机 config.yml：docker run -v /path/config.yml:/config.yml -v /path/users.properties:/users.properties ...
ADD blacklist.html /blacklist.html
ADD users.properties /users.properties
ADD pac.txt /pac.txt

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /app.jar"]
