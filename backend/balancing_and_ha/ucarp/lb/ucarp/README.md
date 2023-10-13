### UCARP

### Solution

https://askubuntu.com/questions/1149275/using-netplan-with-ucarp

Up & Down hooks

```
sudo mkdir -b /usr/share/ucarp

sudo cp ucarp/vip-up.sh /usr/share/ucarp/vip-up.sh
sudo cp ucarp/vip-down.sh /usr/share/ucarp/vip-down.sh
```


Service config here

```
sudo nano /etc/systemd/system/ucarp.service
```

Enable as system service

```
sudo systemctl enable ucarp
```

See `sudo systemctl status ucarp.service` and `sudo journalctl -xe` for details.

For foreground logs 

```
sudo journalctl -xef
```


