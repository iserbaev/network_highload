## Keepalived

### Установка

`apt install keepalived`

### Настройка ядра

`sysctl net.ipv4.ip_nonlocal_bind=1`

### Конфигурация

```
vrrp_instance VI_1 {
    state MASTER
    interface eth0
    virtual_router_id 1
    priority 150
    advert_int 1
    authentication {
        auth_type PASS
        auth_pass SECRET_PASS
    }
    virtual_ipaddress {
        10.10.8.10
    }
}
```

