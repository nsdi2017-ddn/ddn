#!/bin/bash

# compile and run basic programs
cd ~/front_server/GroupManager
mvn package
cd ~/spark/DecisionMaker
mvn package
cd ~

#tmux start
tmux new -s algo_cmp -d
tmux send-keys -t algo_cmp 'sudo /usr/share/kafka/bin/zookeeper-server-start.sh /usr/share/kafka/config/zookeeper.properties' C-m
tmux split-window -t algo_cmp
tmux send-keys -t algo_cmp 'sudo /usr/share/kafka/bin/kafka-server-start.sh /usr/share/kafka/config/server.properties' C-m
tmux new-window -t algo_cmp
tmux send-keys -t algo_cmp 'cd /usr/share/spark' C-m
tmux send-keys -t algo_cmp 'sudo bin/spark-submit --class frontend.DecisionMaker --master local --executor-memory 30G --total-executor-cores 1 --executor-cores 1 ~/spark/DecisionMaker/target/DecisionMaker-1.0-SNAPSHOT.jar localhost:9092 internal_groups decision'
tmux split-window -t algo_cmp
tmux send-keys -t algo_cmp 'tmux select-pane -t 0' C-m
sleep 0.5
tmux split-window -t algo_cmp
tmux split-window -t algo_cmp
tmux send-keys -t algo_cmp 'cd /var/www/info' C-m
tmux send-keys -t algo_cmp 'tmux select-pane -t 1' C-m
sleep 0.5
tmux send-keys -t algo_cmp 'tmux kill-pane' C-m
sleep 0.5
tmux new-window -t algo_cmp
tmux send-keys -t algo_cmp 'cd front_server/GroupManager' C-m
tmux send-keys -t algo_cmp 'java -cp target/GroupManager-1.0-SNAPSHOT.jar frontend.GroupManager frontend1 localhost ../gmConfig'
