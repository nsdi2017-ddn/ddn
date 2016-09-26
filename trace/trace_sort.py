#!/usr/bin/python

import sys

fin = open(sys.argv[1])
records = fin.readlines()
fin.close()
records = sorted(records, key=lambda record:record.split("\t", 1)[0])
fout = open(sys.argv[2], "w")
fout.writelines(records)
fout.close()
