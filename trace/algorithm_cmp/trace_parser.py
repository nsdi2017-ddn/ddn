#!/usr/bin/python

# parse the trace and test
#
# Author: Shijie Sun
# Email: septimus145@gmail.com
# Sept, 2016

import time
import threading
import urllib
import urllib2
import Queue
import sys
from itertools import izip


TIMEOUT = 3     # timeout restriction for requests
UPDATE_DELAY = 2    # delay time from receiving decision to send update


URL = ''
trace_start_time = 0
trace_finish_time = 0
update_queue = Queue.Queue() # message queue for request
session_queue = Queue.Queue() # store the finished session requests for output to file
request_num = []   # number of requests sent and succeeded [[send1, succeeded1], ... , [send2, succeeded2]]
load_dict_list = []
cost_list = []


def request_performer(*trace):
    global update_queue
    global request_num
    global load_dict_list
    global cost_list
    global session_queue

    curr_second = trace[0] - trace_start_time
    curr_minute = curr_second / 60
    request_num[curr_second][0] += 1
    values = {'payload' : trace[1] + '\t'.join(trace[2].keys()), 'method' : 'request'}
    decision = ''
    try:
        con = urllib2.urlopen(URL, urllib.urlencode(values), timeout=TIMEOUT)
        decision = con.read().strip()
    except Exception as inst:
        print(inst)
        return
    # if decision is not in decision_list
    if not trace[2].has_key(decision):
        return
    request_num[curr_second][1] += 1
    # update the load dict
    if not load_dict_list[curr_minute].has_key(decision):
        load_dict_list[curr_minute][decision] = 1
    else:
        load_dict_list[curr_minute][decision] += 1
    cost_factor = 1
    if sum(load_dict_list[curr_minute].values()) > 0:
        load = load_dict_list[curr_minute][decision] / float(load_dict_list[curr_minute]['total_sessions'])
        for key in sorted(trace[3][decision].keys(), reverse=True):
            if load > key:
                cost_factor = trace[3][decision][key]
                break
    cost_factor = 1
    cost = cost_factor * float(trace[2][decision])
    cost_list[curr_second] += cost
    update_str = trace[1] + decision + '\t' + str(cost)
    update_queue.put([time.time() + UPDATE_DELAY, update_str])
    session_queue.put("%d\t%f\t%s\n"%(trace[0], cost, decision))


def update_performer():
    global update_queue
    while True:
        while update_queue.empty():
            time.sleep(0.05)
        info = update_queue.get()
        while time.time() < info[0]:
            time.sleep(0.05)
        try:
            con = urllib2.urlopen(URL, urllib.urlencode({'payload' : info[1], 'method' : 'update'}), timeout=TIMEOUT)
        except Exception as inst:
            print(inst)


if __name__ == '__main__':
    #global URL
    #global trace_start_time
    #global trace_finish_time
    #global update_queue
    #global request_num
    #global load_dict_list
    #global cost_list

    if len(sys.argv) < 3:
        print "Usage: ", sys.argv[0], "url trace_file"
        sys.exit(1)
    URL = sys.argv[1]
    trace_list = []

    # load the trace
    with open(sys.argv[2]) as fin:
        # seek to the beginning of the file and read all traces
        fin.seek(0)
        for trace in fin.readlines():
            [feature, info] = trace.split('DecisionMap')
            trace_time = int(feature.split('\t',1)[0]) / 1000
            [decision_str, load_str] = info.strip().split('LoadMap')
            decision_map = dict(decision.split(',') for decision in decision_str.strip().split('\t'))
            load_map = dict([load.split(',')[0], load.split(',')[1].split(';')] for load in load_str.strip().split('\t'))
            for load in load_map:
                load_map[load] = dict(zip(load_map[load][0::2], load_map[load][1::2]))
            trace_list.append([trace_time, feature, decision_map, load_map])

    # initialize
    trace_start_time = trace_list[0][0]
    trace_stop_time = trace_list[len(trace_list) - 1][0]
    request_num = [[0,0] for i in range(trace_stop_time - trace_start_time + 1)]
    load_dict_list = [{} for i in range((trace_stop_time - trace_start_time)/60 + 1)]
    cost_list = [0 for i in range(trace_stop_time - trace_start_time + 1)]

    for load_dict in load_dict_list:
        load_dict['total_sessions'] = 0
    for trace in trace_list:
        load_dict_list[(trace[0] - trace_start_time) / 60]['total_sessions'] += 1

    update_thread = threading.Thread(target=update_performer)
    update_thread.daemon = True
    update_thread.start()

    test_start_time = time.time()
    test_second = 0
    send_num = 0

    fout = open(sys.argv[2] + '.res','w')
    fout1 = open(sys.argv[2] + '.sepa','w')
    # start the test
    print "------------------------------ %3d sec" % test_second
    for trace in trace_list:
        while (time.time() - test_start_time) < (trace[0] - trace_start_time):
            time.sleep(0.05)
        if int(time.time() - test_start_time) > test_second:
            test_second = int(time.time() - test_start_time)
            if request_num[test_second-1][1] > 0:
                print "| send %d, average cost %f" % (send_num, cost_list[test_second-1]/request_num[test_second-1][1])
                send_num = 0
                fout.write(str(cost_list[test_second-1] / request_num[test_second-1][1]) + '\n')
            print "------------------------------ %3d sec" % test_second
            while not session_queue.empty():
                fout1.write(session_queue.get())
        thread = threading.Thread(target=request_performer, args=(trace))
        thread.daemon = True
        thread.start()
        send_num += 1

    # wait all the requests and updates are finished
    time.sleep(TIMEOUT * 2)

    fout.close()
    while not session_queue.empty():
        fout1.write(session_queue.get())
    fout1.close()
    print request_num
    print cost_list
