1) Скачаем docker-compose.yml, которым будем пользоваться в дальнейшем.
   curl https://raw.githubusercontent.com/citusdata/docker/master/dockercompose.yml > docker-compose.yml
2)

```shell 
POSTGRES_PASSWORD=postgres docker-compose -p citus -f citus-dc.yaml up --scale worker=2 -d
```

3) Подключимся к координатору:

 ```shell  
    docker exec -it citus_master psql -U postgres
```

4) Таблицы будут созданы при запуске микросервисов с ключиком --force-migrate
   4 базы данных, каждая для своего микросервиса - auth/conversation/post/nh_user
   В рамках задания будут шардированы таблички в database conversation - table conversation_log, table message_log
5) Создадим распределенные (шардированные) таблицы
    - Люди обычно просматривают все сообщения одного чата, при этом сначала более новые сообщения, потом более старые.
      Соответственно стоит хранить данные, относящиеся к одному чату и близкие по времени на одном шарде.
    - Человек (пользователь или пользователи в групповой чат) одновременно может писать много сообщений. Обычно
      неравномерно распределено количество сообщений между разными чатами и реже бывает пользователь, который пишет
      много сообщений в разные чаты одновременно. Поэтому стоит группировать сообщения по идентификатору чата и времени
      написания сообщения.
      Так как в чат пишут обычно как минимум два человека, то хранить рядом сообщения одного конкретного пользователя (
      по fromId) не логично, так как запросов на сообщения одного конкретного пользователя не будет.

```shell  
SELECT create_distributed_table('conversation_log', 'id');
SELECT create_distributed_table('message_log', 'conversation_id, created_at');
```

       В качестве руководства использовал https://docs.citusdata.com/en/v11.3/use_cases/multi_tenant.html#multi-tenant-applications 

6) Наполним данными:
   Сначала заполним табличку users, используя метод из предыдущего задания (999 000 строк),
   потом скачал userId, и на их основе сгенерировал диалоги для двух таблиц conversation_log, message_log.

7) Посмотрим план запроса. Видим, что select теперь распределенный и пойдет
   на все шарды:

```shell
postgres=# explain select * from conversation_log limit 10;
                                                     QUERY PLAN                                                     
--------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.00..0.00 rows=10 width=57)
   ->  Custom Scan (Citus Adaptive)  (cost=0.00..0.00 rows=100000 width=57)
         Task Count: 32
         Tasks Shown: One of 32
         ->  Task
               Node: host=citus_worker_1 port=5432 dbname=postgres
               ->  Limit  (cost=0.00..0.20 rows=10 width=57)
                     ->  Seq Scan on conversation_log_102008 conversation_log  (cost=0.00..19.60 rows=960 width=57)
(8 rows)

``` 

8) Посмотрим план запроса по конкретному id. Видим, что такой select
   отправится только на один из шардов:

```shell
postgres=# explain select * from conversation_log where id = '2bfdc572-dbcf-467d-aca2-19cda052a744' limit 10;
                                                     QUERY PLAN                                                     
--------------------------------------------------------------------------------------------------------------------
 Custom Scan (Citus Adaptive)  (cost=0.00..0.00 rows=0 width=0)
   Task Count: 1
   Tasks Shown: All
   ->  Task
         Node: host=citus_worker_1 port=5432 dbname=postgres
         ->  Limit  (cost=4.19..12.66 rows=5 width=57)
               ->  Bitmap Heap Scan on conversation_log_102008 conversation_log  (cost=4.19..12.66 rows=5 width=57)
                     Recheck Cond: (id = '2bfdc572-dbcf-467d-aca2-19cda052a744'::uuid)
                     ->  Bitmap Index Scan on conversation_log_pkey_102008  (cost=0.00..4.19 rows=5 width=0)
                           Index Cond: (id = '2bfdc572-dbcf-467d-aca2-19cda052a744'::uuid)
(10 rows)


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