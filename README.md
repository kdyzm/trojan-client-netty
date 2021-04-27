# socks5-netty

## 一.项目运行

项目启动方法：

```bash
mvn clean package
```

打包完成之后使用`java -jar` 命令启动即可

温馨提示：windows环境无法直接使用该程序，需配合 [Proxifier](https://www.proxifier.com/download/) 软件一起使用

## 二.配置文件详解

|配置文件名|配置文件描述|
|---|---|
|config.properties|根配置文件|
|users.properties|默认的授权用户文件|
|blacklist.txt|默认的域名黑名单|

### 1.config.properties

|配置项|配置描述|
|---|---|
|authentication|true或者false，是否启用认证的开关|
|server.port|服务启动的端口号|
|authentication.path|当authentication配置项为true时生效，表示授权用户文件路径|
|blacklist.path|黑名单文件所在路径|

### 2.users.properties

用户授权文件，里面为`用户名=密码`格式的键值对

### 3.blacklist.txt
该文件是域名黑名单配置文件，每行一个域名，使用`baidu.com`的形式进行后缀匹配

在黑名单中的请求，如果是http请求，则会返回一个提示页面在黑名单中拒绝访问；如果是https请求或者其它任何协议的请求，直接拒绝连接。



