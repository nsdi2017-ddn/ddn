#!/bin/bash

for feature_type in jointime bufratio; do
    for group_id in 20115 22773 11351 6830 7922 7018 209 701 20001; do
        for algorithm in ucb newucb c3 eg akamai level3; do
            ./algorithm_cmp/combine.py result0916/$feature_type/$algorithm/example-Group-$group_id-AllEpochs-trace.txt.res
            mv result0916/$feature_type/$algorithm/example-Group-$group_id-AllEpochs-trace.txt.res.cmb $algorithm.cmb
        done
        cat cost.conf | gnuplot
        mv cost.png result0916/$feature_type/figures/Group-$group_id.png
        rm *.cmb
    done
done
