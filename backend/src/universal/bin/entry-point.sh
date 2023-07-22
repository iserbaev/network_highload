#!/usr/bin/env bash

case $1 in
user-service-cli)
    shift
    exec "$(dirname "$0")"/user-service-cli "$@"
    ;;

nh-client-cli)
    shift
    exec "$(dirname "$0")"/nh-client-cli "$@"
    ;;

bash)
    shift
    exec bash "$@"
    ;;

*)
    echo "Unknown command: '$1'"
    echo "Available commands: user-service-cli, nh-client-cli, bash"
esac

