#!/usr/bin/python

import time
import threading
import urllib
import urllib2
import Queue
import math
import platform

TIMEOUT = 3     # timeout restriction for requests
URL = 'http://10.1.1.2/proxy.php'
RPS = 680      # request per second
Time = 80       # total time for request sending
LengthCheck = False	# check the length of response content
CONTENTLENGTH = 43	# length of response content, need enable LengthCheck
DISTRIBUTE = True       # does the program run distributed

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
    values = {'os':'os_x','isp':'comcast','score':'35','meaningless':'qwe'*100}
    post_data = urllib.urlencode(values)    # encode the payload
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
    threads = dict()
    req_fns_msg.queue.clear()
    results_list = list()
    collector = results_collector(req_fns_msg, threads, results_list)
    collector.daemon = True
    collector.start()
    start_time = time.time()
    request_generator(threads)
    stop_time = time.time()

    time.sleep(5) # wait requests to finish
    print "--------Processing the result----------"
    print "Test time: %f s"%(stop_time-start_time)
    print "Total %d requests sent, %d succeeded"%(sent_requests, successful_requests)

    if successful_requests > 0 and not DISTRIBUTE:
        response_time = (zip(*results_list))[3]
        print "Average response time: %f s"%(sum(response_time) / float(len(response_time)))
        sorted_results = sorted(results_list, key=lambda result:result[1])
        By_request = [[0,0] for i in range(int(math.ceil(sorted_results[successful_requests-1][1]-start_time)) + 1)]
        for result in sorted_results:
            interval = int(math.ceil(result[1] - start_time))-1
            By_request[interval][0] += result[3]
            By_request[interval][1] += 1
        for byreq in By_request:
            if byreq[1]!= 0:
                byreq[0] = round((byreq[0] / byreq[1]), 3)
        print "By sending time per second: [Response time,  successful requests]"
        print By_request
        sorted_results = sorted(results_list, key=lambda result:result[2])
        By_response = [[0,0] for i in range(int(math.ceil(sorted_results[successful_requests-1][2]-start_time)) + 1)]
        for result in sorted_results:
            interval = int(math.ceil(result[2] - start_time))-1
            By_response[interval][0] += result[3]
            By_response[interval][1] += 1
        for byres in By_response:
            if byres[1]!=0:
                byres[0] = round((byres[0] / byres[1]), 3)
        print "By receving time per second: [Response time,  successful requests]"
        print By_response

    elif DISTRIBUTE:
        platname = (platform.node()).split('.')[0]
        f=open(platname + ".bm", 'w')
        f.write(str([sent_requests,(stop_time-start_time), RPS]) + '\n')
        for result in results_list:
            f.write(str(result) + '\n')
        f.close()

