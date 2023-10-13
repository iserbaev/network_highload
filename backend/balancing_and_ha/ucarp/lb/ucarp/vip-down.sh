#!/bin/sh
exec 2>/dev/null

echo "[UCARP]: DOWN-HOOK called. Del VIP = $2 for $1"

/sbin/ip address del "$2"/32 dev "$1"
