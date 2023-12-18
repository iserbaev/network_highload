1) Скачаем docker-compose.yml, которым будем пользоваться в дальнейшем.
   curl https://raw.githubusercontent.com/citusdata/docker/master/dockercompose.yml > docker-compose.yml
2)

```shell
cd dc
docker-compose -p citus -f citus-dc-nh.yaml up --scale worker=2 -d
```

3) Подключимся к координатору:

 ```shell  
    docker exec -it citus_master psql -U postgres
```

4) Таблицы будут созданы при запуске микросервиса с ключиком --force-migrate
   В рамках задания будут шардированы таблички в database nh - table balance_commands_log, balance_events_log

```shell  
SELECT create_distributed_table('balance_commands_log', 'transaction_id, created_at');
SELECT create_distributed_table('balance_events_log', 'account_id, created_at');
```

5) Посмотрим, видит ли координатор новые шарды:

```shell
postgres=# \c nh;
postgres=# SELECT master_get_active_worker_nodes();
 master_get_active_worker_nodes 
--------------------------------
 (citus_worker_2,5432)
 (citus_worker_1,5432)
(2 rows)


```

6) Проверим, на каких узлах лежат сейчас данные:

```shell
postgres=# SELECT nodename, count(*) FROM citus_shards GROUP BY nodename;
    nodename    | count 
----------------+-------
 citus_worker_1 |    32
 citus_worker_2 |    32
(2 rows) 
```

7) Transfer command
```bash
curl -X 'POST' \
  'http://localhost:8033/wallet/balance_transfer_command' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "fromAccount": "test_account",
  "toAccount": "in",
  "amount": 1000,
  "currencyType": "USD",
  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa3"
}'
```

```bash
curl -X 'POST' \
  'http://localhost:8033/wallet/balance_transfer_command' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "fromAccount": "test_account",
  "toAccount": "in",
  "amount": 2500,
  "currencyType": "USD",
  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa4"
}'
```

8) Check balance

```bash
curl -X 'GET' \
  'http://localhost:8033/wallet/balance/stream/test_account' \
  -H 'accept: text/event-stream'  \
  -H 'Authorization: Bearer asasg'
```