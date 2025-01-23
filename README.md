# trojan-client-netty

一直想用java写一个trojan客户端，但是似乎一直没人搞，连个借鉴都没有。。。
研究了下trojan协议，仔细看下其实比socks5协议简单很多，
这里使用socks5或者http协议做本地服务器代理，使用trojan协议作为科学上网客户端，如果启用trojan，一定要确保有个远端trojan服务端。

相关设计详见我的博客文章：

[使用netty实现socks5协议](http://blog.kdyzm.cn/post/70)

[使用netty实现socks5+trojan混合协议trojan客户端](http://blog.kdyzm.cn/post/71)

|版本号|说明|
|---|---|
|v1.0|实现Socks5无认证服务端|
|v2.0|1. 集成spring<br/>2. 新增配置文件<br/>3. 实现用户认证|
|v3.0|新增黑名单功能|
|v4.0|socks5代理集成trojan客户端|
|v5.0|实现pac模式，删除黑名单功能，统一集成到pac文件|
|v5.1|集成http/https本地代理|
|v6.0|http/https代理集成trojan客户端|

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
|config.yml|根配置文件|
|users.properties|默认的授权用户文件|
|pac.txt|默认的地址行为控制名单|

### 1.config.yml

```yaml
#设置端口号
server:
  port:
    socks5: 10808
    http: 10809

#代理模式有 pac|global|direct三种
proxy:
  mode: global

socks5:
  authentication:
    #true或者false，是否启用认证的开关
    enabel: true
    #socks5认证信息文件，当authentication配置项为true时生效，表示授权用户文件路径，存储着用户名:密码键值对
    path: ./users.properties

trojan:
  #trojan密码，用于trojan协议握手
  password: 密码
  server:
    #trojan服务端域名
    host: 服务器域名
    #trojan服务端tls端口号
    port: 443

#pac文件路径，0：直连；-1：拒绝连接；1：代理连接
pacfile:
  path: ./pac.txt
```

### 2.users.properties

用户授权文件，里面为`用户名=密码`格式的键值对，用于socks5认证

### 3.pac.txt

该文件是pac文件，里面均为`域名:1|0|-1`格式的记录,1表示使用代理连接，0表示直连，1表示拒绝连接。 例如`baidu.com:-1`表示不允许连接baidu.com域名。

请求地址在pac名单中的按照名单中指定的值确定行为，如果请求的地址不在该名单中，默认行为取决于代理模式

- 如果代理模式是global，默认行为是走代理
- 如果代理模式是pac，默认行为是直连
- 如果代理模式是direct，默认行为是直连

该文件也是域名黑名单配置文件，在`proxy.mode`的三种代理模式之前生效

在黑名单中的请求，如果是http请求，则会返回一个提示页面在黑名单中拒绝访问；如果是https请求或者其它任何协议的请求，直接拒绝连接。

## 三、注意事项

### 1.连接速度缓慢，有些网页打不开

使用官方提供的v2rayN等工具速度很快，但是使用这个程序速度很慢甚至打不开一些网页，造成这个的原因在于 Proxifier没设置好，一定要注意使用代理的dns设置，菜单：Profile->Name Resolution
取消`Detect DNS settings automatically`选项，勾选`Resolve hostnames through proxy`，之后就好了


### 2.补充本项目的作用图例：

![image-20250123171816933](https://blog.kdyzm.cn/blog/public/2025/01/23/143e44b6-e023-4dab-b516-1ee1ccc5fb9e.png)
