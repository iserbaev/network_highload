## Отказоустойчивость приложений

### Cluster PostgresSQL

```shell
docker-compose -p nh -f dc-postgres-cluster.yml up --detach --scale postgresql-master=1 --scale postgresql-slave=2
```
### Haproxy LB for PostgresSQL cluster
```shell
docker-compose -p nh -f dc-nh-haproxy.yml up -d
```

### Tarantool InMemory DB store for messages

```shell
docker-compose -p nh -f dc-tarantool.yml up -d
```
### Graph Database for store user relations

```shell
docker-compose -p nh -f dc-neo4j.yml up -d
```

### Run auth, user, post MSI's
```shell
docker-compose -f dc-nh-local.yml up -d
```

### Run Conversation MSI's

```shell
docker-compose -f dc-nh-local-conversation.yml up --detach --scale nh-conversation=2
```

### Run NGINX
```shell
docker network create otus-nginx-demo
docker-compose -p nh -f dc-nh-nginx.yml up -d
```

### Stop one database container
```shell
docker stop nh_postgresql-slave_1
```

```shell
[WARNING]  (8) : Server pgReadOnly/pg1 is DOWN, reason: Layer7 invalid response, info: "FATAL", check duration: 2ms. 1 active and 0 backup servers left. 12 sessions active, 0 requeued, 0 remaining in queue.
```
### Stop conversation application instance
```shell
docker stop backend-2
```

- run request from client
```shell
curl -X 'POST' \
  'http://localhost:8082/dialog/4396041a-855e-4506-b412-f8307ae89c0e/send' \
  -H 'accept: application/json' \
  -H 'Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE2OTcyMjE3MzUsImlhdCI6MTY5NzIyMTEzNSwiaWQiOiJlNjVlNDZhNC1lY2EwLTRlOGItOWNlYi1lOTRjM2MzNzIzNTIiLCAicGFzc3dvcmQiOiJzdHJpbmcifQ.XjJroig-61IbdPJunFwHnlvNYCvuAZ5ulLvP4K-NY2U' \
  -H 'Content-Type: application/json' \
  -d '{
  "text": "string"
}'

```
logs from nginx
```shell
172.21.0.1 - - [13/Oct/2023:18:24:27 +0000] "POST /dialog/4396041a-855e-4506-b412-f8307ae89c0e/send HTTP/1.1" 200 5 "-" "curl/8.1.2" "-"
```