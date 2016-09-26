#!/usr/bin/python

import urllib
import urllib2
import random

if __name__ == '__main__':
    url = 'http://10.11.10.5:80/update.php'
    with open("temp.dat") as f:
        keys = f.readline().split('\t')
        record = f.readline()
        while record:
            score = random.randint(0,100)
            decision = "decision1"
            data = dict()
            data['payload'] = record.rstrip() + '\t' + str(score) + '\t' + decision
            post_data = urllib.urlencode(data)
            req = urllib2.Request(url, post_data)
            con = urllib2.urlopen(req)
            print con.read()
            record = f.readline()
