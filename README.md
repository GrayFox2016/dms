# dms
Docker Container Manage System, Like A Mini Kubernetes.

## Start to deploy a master server


### 1. Build with gradle

> gradle buildToRun

### 2. Run using groovy command(You need download groovy 2.4.x+ local)

```
cd dms
groovy -cp dist\*:src:resources src/RunServer.groovy
```

TIPS: By default, dms use h2 database and data file is /var/dms/data*, and dms will create tables when first run. DDL refer init_h2.sql


### 3. Run nginx to deploy front web ui

Use nginx.conf and change root location: 

```
        location / {
            root   /ws/dms/www;
            index  index.html index.htm;
        }

        location /dms {
            proxy_pass   http://127.0.0.1:5000;
        }
```

### 4. View web ui and login using any username and password

### 5. Update scripts using by node agent to database table

first login, then open url: http://nginx/dms/agent/script/update/batch

### 6. Create first cluster in web ui


### Others: Change if you need use another database server

DMS use SPI to support user define datasource/login service/distributed lock etc.
You need to create a class implements DataSourceCreator in package vendor.

Next TODO: change ${projectRoot}/src/conf.properties

```
db.host=***
db.port=***
db.url=***
db.user=***
db.password=***
```

## Deply a node

### 7. Install docker/boot2docker, open -H 0.0.0.0:2376

### 8. change src/conf.properties

### 9. Run using groovy command(You need download groovy 2.4.x+ local)

```
cd dms
groovy -cp dist\*:src:resources src/RunAgent.groovy
```

TIPS:

If you run node agent in windows using boot2docker like me.

need system property set DOCKER_CERT_PATH, and run with another parameter

nodeIpDockerHost=***


### Then enjoy 