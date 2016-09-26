#!/bin/bash

feature_type=jointime
for group_id in 6830 209 20001; do
    for algorithm in ucb newucb c3 eg akamai level3; do
        ./combine.py result0917/$feature_type/$algorithm/example-Group-$group_id-AllEpochs-trace.txt.res
        mv result0917/$feature_type/$algorithm/example-Group-$group_id-AllEpochs-trace.txt.res.cmb $algorithm.cmb
    done
    cat cost.conf | gnuplot
    mv cost.png result0917/$feature_type/figures/Group-$group_id.png
    rm *.cmb
done
feature_type=bufratio
for group_id in 7922 209 7018; do
    for algorithm in ucb newucb c3 eg akamai level3; do
        ./combine.py result0917/$feature_type/$algorithm/example-Group-$group_id-AllEpochs-trace.txt.res
        mv result0917/$feature_type/$algorithm/example-Group-$group_id-AllEpochs-trace.txt.res.cmb $algorithm.cmb
    done
    cat cost.conf | gnuplot
    mv cost.png result0917/$feature_type/figures/Group-$group_id.png
    rm *.cmb
done
