#!/bin/bash

k=10
URL="http://10.11.10.2/player.php"
throughput=()
responseTime=()

for j in $(seq 1 $k); do
ab -n 100000 -c 7 -T 'application/x-www-form-urlencoded' -r -p post.data $URL > out
i=0
while IFS='' read -r line || [[ -n "$line" ]]; do
    let i+=1
    if [ $i -eq 22 ];then
	IFS=' ' read -ra ADDR <<< "$line"
        throughput+=(${ADDR[3]})
    fi
    if [ $i -eq 23 ];then
	IFS=' ' read -ra ADDR <<< "$line"
        responseTime+=(${ADDR[3]})
    fi
done < "out"
sleep 10
done
echo ${throughput[*]}
echo ${responseTime[*]}
printf "\n"
sumt=$( IFS="+"; bc <<< "${throughput[*]}" )
echo "Throughput:  $(bc -l <<< "$sumt/$k")"
sumr=$( IFS="+"; bc <<< "${responseTime[*]}" )
echo "Response Time:  $(bc -l <<< "$sumr/$k")"
