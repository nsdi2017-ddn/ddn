#!/usr/local/bin/python
import sys

fin = open(sys.argv[1])
list1 = []
list11 = []
list2 = []
list22 = []
RPS = 100
for line in fin:
    record = line.split(",")
    if record[0] == '0':
        list1.append(record[2].strip())
    else:
        list2.append(record[2].strip())
i = 0
s = 0
for record in list1:
    i+=1
    s+=float(record)
    if i == RPS:
        i = 0
        list11.append(s/RPS)
        s = 0
i = 0
s = 0
for record in list2:
    i+=1
    s+=float(record)
    if i == RPS:
        i = 0
        list22.append(s/RPS)
        s = 0
fout = open(sys.argv[2], 'w')
for i in range(min(len(list11), len(list22))):
    fout.write("%f\t%f\n"%(list11[i], list22[i]))
print abs(len(list11) - len(list22))

