#!/usr/bin/python

import urllib
import urllib2
import random
import time
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

if __name__ == '__main__':
    url = 'http://10.1.1.4:80/proxy.php'
    title = 'Sent to Proxy Server (static url)'
    score = str(random.randint(0,100))
    print "score" + score
    values = {'os':'os_x', 'isp':'comcast', 'score':score, 'omit':'yy'*100}
    data = urllib.urlencode(values)
    req = urllib2.Request(url, data)
    CDF = []
    for i in range(1000):
            last = time.time()
            con = urllib2.urlopen(req)
            CDF.append((time.time() - last) * 1000)
    print con.read()
    CDF = sorted(CDF)
    print '------' + str(sum(CDF)/len(CDF))
    fig = plt.figure()
    ax = fig.add_subplot(111)
    y_value = [y / float(len(CDF)) for y in range(len(CDF))]
    line1, = ax.plot(CDF, y_value, color='b')
    ax.set_xlabel('Response Time (millisecond)',fontsize=18)
    ax.set_ylabel('Percentage',fontsize=18)
    fig.suptitle(title,fontsize=20)
    fig.savefig(title.replace(' ','_')  + '.png')
