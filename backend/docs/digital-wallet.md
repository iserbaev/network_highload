1) Скачаем docker-compose.yml, которым будем пользоваться в дальнейшем.
   curl https://raw.githubusercontent.com/citusdata/docker/master/dockercompose.yml > docker-compose.yml
2)

```shell 
docker-compose -p citus -f citus-dc-nh.yaml up --scale worker=2 -d
```

3) Подключимся к координатору:

 ```shell  
    docker exec -it citus_master psql -U postgres
```

4) Таблицы будут созданы при запуске микросервиса с ключиком --force-migrate
   3 базы данных, каждая для своего микросервиса - auth/conversation/post/nh_user
   В рамках задания будут шардированы таблички в database nh - table balance_commands_log, balance_events_log

```shell  
SELECT create_distributed_table('balance_commands_log', 'transaction_id, created_at');
SELECT create_distributed_table('balance_events_log', 'account_id, created_at');
```

9) Добавим еще парочку шардов:
   ```shell POSTGRES_PASSWORD=postgres docker-compose -p citus -f citus-dc.yaml up --scale worker=5 -d```

10) Посмотрим, видит ли координатор новые шарды:

```shell
postgres=# SELECT master_get_active_worker_nodes();
 master_get_active_worker_nodes 
--------------------------------
 (citus_worker_2,5432)
 (citus_worker_1,5432)
(2 rows)


```

11) Проверим, на каких узлах лежат сейчас данные:

```shell
postgres=# SELECT nodename, count(*) FROM citus_shards GROUP BY nodename;
    nodename    | count 
----------------+-------
 citus_worker_1 |    32
 citus_worker_2 |    32
(2 rows) 
```

12) Видим, что данные не переехали на новые узлы, надо запустить
    перебалансировку.
13) Для начала установим wal_level = logical чтобы узлы могли переносить
    данные:

```shell
postgres=# alter system set wal_level = logical;
ALTER SYSTEM
postgres=# SELECT run_command_on_workers('alter system set wal_level = logical');
         run_command_on_workers         
----------------------------------------
 (citus_worker_1,5432,t,"ALTER SYSTEM")
 (citus_worker_2,5432,t,"ALTER SYSTEM")
 (citus_worker_3,5432,t,"ALTER SYSTEM")
 (citus_worker_4,5432,t,"ALTER SYSTEM")
 (citus_worker_5,5432,t,"ALTER SYSTEM")
(5 rows)

```

15) Перезапускаем все узлы в кластере, чтобы применить изменения wal_level.

```shell
POSTGRES_PASSWORD=postgres docker-compose -p citus -f citus-dc.yaml restart
```

16) Проверим, что wal_level изменился:

```shell
backend % docker exec -it citus_worker_1 psql -U postgres
psql (15.3 (Debian 15.3-1.pgdg120+1))
Type "help" for help.

postgres=# show wal_level;
 wal_level 
-----------
 logical
(1 row)

```

17) Запустим ребалансировку:

```shell
backend % docker exec -it citus_master psql -U postgres;
psql (15.3 (Debian 15.3-1.pgdg120+1))
Type "help" for help.

postgres=# SELECT citus_rebalance_start();
NOTICE:  Scheduled 18 moves as job 1
DETAIL:  Rebalance scheduled as background job
HINT:  To monitor progress, run: SELECT * FROM citus_rebalance_status();
 citus_rebalance_start 
-----------------------
                     1
(1 row)

```

18) Следим за статусом ребалансировки, пока не увидим там соообщение

```shell
postgres=# SELECT * FROM citus_rebalance_status();
 job_id |  state   | job_type  |           description           |          started_at           |          finished_at          |                     details                      
--------+----------+-----------+---------------------------------+-------------------------------+-------------------------------+--------------------------------------------------
      1 | finished | rebalance | Rebalance all colocation groups | 2023-09-18 18:55:15.754247+00 | 2023-09-18 18:57:20.288075+00 | {"tasks": [], "task_state_counts": {"done": 18}}
(1 row)

```

19) Проверяем, что данные равномерно распределились по шардам:

```shell
postgres=#     SELECT nodename, count(*)
    FROM citus_shards GROUP BY nodename;
    nodename    | count 
----------------+-------
 citus_worker_1 |    14
 citus_worker_5 |    12
 citus_worker_2 |    14
 citus_worker_4 |    12
 citus_worker_3 |    12
(5 rows)

```