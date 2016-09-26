#!/usr/bin/python

# Slave program of distributed benchmark

import time
import threading
import urllib
import urllib2
import Queue
import math
import platform
import sys
import socket

TIMEOUT = 3     # timeout restriction for requests
URL = 'http://10.1.1.2/proxy.php'
RPS = 800      # request per second
Time = 80       # total time for request sending
LengthCheck = False	# check the length of response content
CONTENTLENGTH = 43	# length of response content, need enable LengthCheck

DELAY = 1.0 / RPS
req_fns_msg = Queue.Queue() # message queue for request
sent_requests = 0   # number of requests sent
successful_requests = 0 # number of successful requests
lengthmis_requests = 0 	# number of requests with wrong response content length

def request_performer(idx, data):
    '''
    perform one HTTP request

    args:
        idx:    sequence number of requests
        data:   the payload HTTP request wants to post
    '''
    global sent_requests
    global req_fns_msg
    global successful_requests
    req_stat = [idx, 0, 0, 0, 0]    # states of request: [request_id, sent_time, received_time, response time, result]
                                    # result : 0:fail to get response,  1:success
    req_stat[1] = time.time()
    sent_requests += 1
    try:
        con = urllib2.urlopen(URL, data, timeout=TIMEOUT) # perform the request
        if LengthCheck:
            text = con.read()
            # print text
	    if len(text) == CONTENTLENGTH:
                req_stat[4] = 1
        else:
            req_stat[4] = 1
        req_stat[2] = time.time()
        req_stat[3] = round((req_stat[2] - req_stat[1]), 6)
    except Exception as inst:
        #print(inst)
        req_stat[4] = 0
    req_fns_msg.put(req_stat)   # send states of request
    if req_stat[4] == 1:
        successful_requests += 1

def request_generator(threads):
    '''
    generate request performer

    args:
        threads:    threads dictionary to handle each thread
    '''
    #values = {'os':'os_x','isp':'comcast','score':'85','meaningless':'qwe'*100}
    #post_data = urllib.urlencode(values)    # encode the payload
    with open("post.data") as f:
        post_data = f.readline()
    print "Size of payload: %d"%len(post_data)
    for idx in range(RPS * Time):
        # create performers for each request
        threads[idx] = threading.Thread(target=request_performer, args=(idx, post_data))
        threads[idx].daemon = True
        threads[idx].start()
        time.sleep(DELAY)

class results_collector(threading.Thread):
    '''
    collect the message of request performer and release relevant thread

    args:
        queue:          message queue
        threads:        threads dictionary to handle each thread
        results_list:   list of test results
    '''
    def __init__(self, queue, threads, results_list):
        threading.Thread.__init__(self)
        self.queue = queue
        self.threads = threads
        self.results_list = results_list
    def run(self):
        while True:
            msg  = self.queue.get()
            del threads[msg[0]]
            if msg[4] == 1:
                results_list.append(msg)

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print "Usage: ", sys.argv[0], "port url"
        sys.exit(1)
    URL = sys.argv[2]

    # bind port to receive the order from master
    platname = (platform.node()).split('.')[0]
    tasksocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    tasksocket.bind(('', int(sys.argv[1])))
    tasksocket.listen(5)

    # loop for orders
    while True:
        print ""
        print ""
        print platname + ": Listening new task..."
        # wait for order from master
        (newconn, newaddr) = tasksocket.accept()
        # get the info from order
        Time, RPS = [int(x) for x in newconn.recv(1024).split()]
        newconn.close()

        # process the order
        print "New task: Time = {0}, RPS = {1}".format(Time,RPS)
        print "Dest URL: " + URL
        DELAY = 1.0 / RPS
        print "Processing..."
        sent_requests = 0
        successful_requests = 0
        lengthmis_requests = 0
        threads = dict()
        req_fns_msg.queue.clear()
        results_list = list()
        collector = results_collector(req_fns_msg, threads, results_list)
        collector.daemon = True
        collector.start()
        start_time = time.time()
        request_generator(threads)
        stop_time = time.time()

        # wait all the requests to finish
        time.sleep(5)
        print "--------Processing the result----------"
        print "Test time: %f s"%(stop_time-start_time)
        print "Total %d requests sent, %d succeeded"%(sent_requests, successful_requests)

        # write the result into file
        f=open(platname + '_' + sys.argv[1] + ".bm", 'w')
        f.write(str([sent_requests,(stop_time-start_time), RPS]) + '\n')
        for result in results_list:
            f.write(str(result) + '\n')
        f.close()
        print "Task Finished!"
