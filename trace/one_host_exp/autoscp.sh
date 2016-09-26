#!/bin/bash


if [ $# -lt 1  ];
then
    echo "Usage: sudo $0 host_name"
    exit 1
fi

scp -r ../../spark $1:~/
scp -r ../../kafka $1:~/
scp -r ../../front_server $1:~/
scp -r ../algorithm_cmp/trace_parser.py $1:~/
scp -r ../real_world_traces/* $1:~/
scp ./onehost_deploy.sh $1:~/
scp ./start_tmux.sh $1:~/
