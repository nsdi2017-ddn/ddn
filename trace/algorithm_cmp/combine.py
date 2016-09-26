#!/usr/local/bin/python
import sys

if __name__ == '__main__':
    window = 60
    with open(sys.argv[1]) as fin:
        with open(sys.argv[1] + '.cmb', 'w') as fout:
            i = 0
            values = []
            s = 0
            for line in fin:
                i += 1
                values.append(float(line))
                s += values[i-1]
                if i >= window :
                    if i % 60 == 0:
                        fout.write('%d\t%f\n' % (i,s/window))
                    s -= values[i-window]

