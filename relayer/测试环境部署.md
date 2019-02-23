# 测试环境部署
## 软硬件环境
1. AWS EC2（t2.xlarge 2CPU 8G）
2. Ubuntu 16.04
3. Docker-ce 18.09.1
4. timescaledb
5. mysql 5.7.18
6. 监控 Kamon + Prometheus + Grafana
7. lightcone(relay)
8. 以太坊节点ganache 
 
测试环境4~8全部采用docker部署，其中以太坊节点ganache只是为了测试使用；生产环境mysql计划采用AWS提供的mysql服务, 以太坊节点生产环境继续使用目前relay1.0中的服务。

## 环境安装
### 一. Docker-ce安装
要创建和管理docker群集，可以Docker Swarm, Kubernetes或Apache Mesos，这里Docker Swarm功能相比其他两者较弱，但已经能很好支持我们目前需求，且运维成本较低，因此我们这里先使用Docker Swarm。

目前最新的docker-ce已经包含了swarm功能，这里我们我们首先需要在所有服务器节点上安装docker-ce。 下面具体说明Docker-ce Community Edition 在Ubuntu上的安装过程：

#### 1. 使用下面的apt命令安装Docker-ce依赖项:
```
sudo apt install apt-transport-https software-properties-common ca-certificates -y
```
#### 2. 将Docker密钥和Docker-ce存储库添加到我们的服务器:
```
curl -fsSL https://download.docker.com/linux/debian/gpg | sudo apt-key add -
sudo echo "deb [arch=amd64] https://download.docker.com/linux/ubuntu xenial stable" > /etc/apt/sources.list.d/docker-ce.list
```
#### 3. 使用下面的apt install命令更新存储库并安装Docker-ce软件包:
```
sudo apt update
sudo apt install docker-ce -y
```
#### 4. 安装完成后，启动docker服务并使其在每次系统引导时启动:
```
systemctl start docker
systemctl enable docker
```
#### 5. Docker-ce现在已经安装在我们的服务器节点上, 其他节点也都需要相同安装。不过这时运行docker命令会发现还需要加sudo权限, 我们可以将docker配置为以非root用户身份运行。比方我们当前非root用户是ubuntu, 则执行:
```
sudo usermod -aG docker ubuntu
```
#### 6. 测试是否安装成功:
```
docker run hello-world
```

参考：
[官方安装指导](https://docs.docker.com/install/linux/docker-ce/ubuntu/)

### 二. timescaleDB安装
timescaleDB是一个开源的并且完全支持SQL的数据库，它是在PostgreSQL数据库的基础上进行开发，所以使用方法基本和传统数据库一致。它可以支持复杂的SQL查询，并针对时间序列数据的快速插入和复杂查询进行了优化，特别适合用于监控，IoT，金融，物流等大数据领域。我们这里利用timescaleDB生成交易的K线数据。
因为AWS上没有timescaleDB服务，我们自己使用docker方式安装。  
#### 获取镜像：
```
docker pull timescale/timescaledb:latest-pg9.6
```   
参考：  
[timescaledb github](https://github.com/timescale/timescaledb)  
[安装](https://docs.timescale.com/v1.2/getting-started/installation/docker/installation-docker)

### 三. mysql安装
测试环境里我们尝试使用docker安装mysql, 生产环境可以直接使用AWS存储服务。  
#### 获取镜像：
```
docker pull mysql:5.7.18
```

### 四. Kamon + Prometheus + Grafana监控环境安装
* lightcone里已经集成了Kamon;
* Kamon是一个有着良好响应性的JVM应用程序的监控工具, 可以把系统运行时的各类metrics通过API提供出来;
* Prometheus是一套开源的系统监控报警框架，Prometheus可以定时采集通过Kamon收集到的metrics;
* Grafana具有功能齐全的度量仪表盘和图形编辑器，有灵活丰富的图形化选项，可以混合多种风格，支持多个数据源特点，Grafana不是必须，Prometheus也可以提供监控界面，但是Grafana显示效果更加理想。  

#### Prometheus 获取镜像：  
```
docker pull prom/prometheus
```   
#### Grafana 获取镜像：  
```
docker pull grafana/grafana
``` 

参考：  
[Kamon](https://kamon.io/docs/latest/guides/getting-started/)  
[Prometheus 入门与实践](https://www.ibm.com/developerworks/cn/cloud/library/cl-lo-prometheus-getting-started-and-practice/index.html)  
[Docker安装Grafana+Prometheus系统监控之Redis](https://www.jianshu.com/p/1cb66d48920b)

### 五. 以太坊节点ganache安装
ganache是一套以太坊网络模拟环境，供测试使用。
#### ganache获取镜像：  
```
docker pull kongliangzhong/loopring-ganache:v2
```

### 六. 创建Swarm集群
测试环境采用 1 manageer + 1 worker 两个节点的集群配置，生产环境建议采用3节点或5节点的方式。
#### 1. 在manager节点初始化Docker Swarm模式:
```
docker swarm init --advertise-addr managerIp
```
将看到manager-node已由manager节点生成。
接下来，我们需要将worker节点添加到集群manager。 为此，我们需要集群“manager”节点中的“join-token”。
#### 2. 在worker节点上运行docker swarm join命令:
```
docker swarm join --token 步骤1中生成的token值 managerIp:2377
```
#### 3. Swarm集群已经创建, 通过在manager节点上运行以下命令来检查它:
```
docker node ls
```
参考:  
[如何在Ubuntu上设置和配置Docker Swarm集群](https://www.howtoing.com/ubuntu-docker-swarm-cluster)

### 七. 安全组配置
目前测试环境relay, timescaledb, mysql, prometheus，ganache服务节点都在一个服务组，AWS上同一个服务组内的各种端口访问权限也是需要配置的，放在同一个服务组的好处是配置之后，后续扩展这个环节不再需要配置。其中swarm涉及的端口比较多，这里列举一下:  

| 协议  | 端口  | 备注 |
|:------------- |:---------------:| -------------:|
| TCP      | 2377 |     swarm + remote mgmt |
| TCP      | 7946 |     swarm |
| UDP      | 7946 |     swarm |
| UDP      | 4789 |     swarm |
| 50 | all       |      swarm |

## Relay集群启动
以下有顺序依赖关系，需要按照序号执行。
### 1. 启动mysql
```
docker run -d --name mysqlService -p 3306:3306 -e MYSQL_ROOT_PASSWORD=111111 mysql:5.7.18
```

### 2. 启动timescaledb
```
docker run -d --name timescaledb -p 5432:5432 -e POSTGRES_PASSWORD=111111 timescale/timescaledb:latest-pg9.6
```

### 3. 配置基础数据
```
insert into `T_MARKET_METADATA` (`status`,`base_token_symbol`,`quote_token_symbol`,`max_numbers_of_orders`,`price_decimals`,`orderbook_agg_levels`,`precision_for_amount`,`precision_for_total`,`browsable_in_wallet`,`update_at`,`base_token`,`quote_token`,`market_hash`) values (1,'LRC','WETH',1000,6,6,5,5,true,0,'0x97241525fe425C90eBe5A41127816dcFA5954b06','0x7cb592d18d0c49751ba5fce76c1aec5bdd8941fc','0xeb9187f4734e15e5f04058f64b9b8194781c0afa');

insert into `T_MARKET_METADATA` (`status`,`base_token_symbol`,`quote_token_symbol`,`max_numbers_of_orders`,`price_decimals`,`orderbook_agg_levels`,`precision_for_amount`,`precision_for_total`,`browsable_in_wallet`,`update_at`,`base_token`,`quote_token`,`market_hash`) values (1,'GTO','WETH',500,6,5,5,5,true,0,'0x2d7233f72af7a600a8ebdfa85558c047c1c8f795','0x7cb592d18d0c49751ba5fce76c1aec5bdd8941fc','0x51c7a126a7fbef75b34e234f39422c1c1c41b669');

insert into `T_TOKEN_METADATA` (`type`,`status`,`symbol`,`name`,`address`,`unit`,`decimals`,`website_url`,`precision`,`burn_rate_for_market`,`burn_rate_for_p2p`,`usd_price`,`update_at`)  
values (0,1,'LRC','LRC','0x97241525fe425c90ebe5a41127816dcfa5954b06','LRC',18,'',6,0.4,0.4,1000,0);

insert into `T_TOKEN_METADATA` (`type`,`status`,`symbol`,`name`,`address`,`unit`,`decimals`,`website_url`,`precision`,`burn_rate_for_market`,`burn_rate_for_p2p`,`usd_price`,`update_at`)  
values (0,1,'GTO','GTO','0x2d7233f72af7a600a8ebdfa85558c047c1c8f795','GTO',18,'',6,0.4,0.4,1000,0);

insert into `T_TOKEN_METADATA` (`type`,`status`,`symbol`,`name`,`address`,`unit`,`decimals`,`website_url`,`precision`,`burn_rate_for_market`,`burn_rate_for_p2p`,`usd_price`,`update_at`)  
values (0,1,'WETH','WETH','0x7cb592d18d0c49751ba5fce76c1aec5bdd8941fc','WETH',18,'',6,0.4,0.4,1000,0);
```

### 4. 启动relay服务
#### 创建服务:
```
docker service create --name relayer --hostname 127.0.0.1 --host mysql-host:172.xx.xx.xx --host ethereum-host:172.xx.xx.xx -p 8080:8080 -d org.loopring/lightcone_actors:latest
``` 
#### 扩展到集群:  
```
docker service scale relayer=2
```

### 5. 启动Prometheus
```
docker run -d -p 9090:9090 --name prometheus -v /Users/ubuntu/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus
```
这里prometheus.yml里主要配置的是Kamon服务地址:
```
global:
  scrape_interval:     15s
  evaluation_interval: 15s
scrape_configs:
  - job_name: 'relayer-test'
    static_configs:
      - targets: ['relay节点IP:9095']
```






