#!/bin/sh
exec 2>/dev/null

echo "[UCARP]: UP-HOOK called. Add VIP = $2 for $1"

/sbin/ip address add "$2"/32 dev "$1"
