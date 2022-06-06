#! /bin/bash

read -p "请输入集群数量: " num

java -jar config.jar -n $num

for i in $(seq 1 $num)
do
    name="$i"
    for i in $(seq 1 `expr 3 - ${#name}`)
    do
        name="0"$name
    done
    name="server"$name".conf"

    java -jar raft.jar --config_path $name > /dev/null &
done

java -jar tester.jar --config_path tester.conf
killall -9 java
