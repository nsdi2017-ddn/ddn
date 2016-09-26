#!/usr/bin/python

# Master program of distributed benchmark

import math
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import socket
import sys

#SlaveList = ['node-5','node-6','node-3','node-4'] # name of slave hosts
SlaveList = ['cp-3', 'cp-2'] # name of slave hosts
#SlaveIPList = ['10.1.1.4','10.1.1.5','10.1.1.6','10.1.1.7'] # IP of slave hosts
SlaveIPList = ['10.11.10.5', '10.11.10.4'] # IP of slave hosts
SlavePortList = [str(i + 2396) for i in range(8)] # ports(slaves) of each host
SlaveNumber = len(SlaveList) * len(SlavePortList) # number of slaves to run together

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print 'Usage: ' + sys.argv[0] + ' Time RPS'
        sys.exit(1)
    Time = int(sys.argv[1])
    RPS = int(sys.argv[2])
    assert (SlaveNumber == len(SlaveIPList) * len(SlavePortList))
    filenames = list()

    # inform slaves to start to send request
    for i in range(len(SlaveIPList)):
        for j in range(len(SlavePortList)):
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.connect((SlaveIPList[i], int(SlavePortList[j])))
            s.sendall(str(Time) + ' ' + str(RPS))
            s.close()
            print SlaveList[i] + ':' + SlavePortList[j] + ' informed!'
            filenames.append(SlaveList[i]+'_'+SlavePortList[j])

    # wait till all slaves finish
    # TODO: let slaves inform master when they finished
    while True:
        isStart = raw_input("Please input 'y' if finished: ")
        if isStart == 'y':
            break

    # Read the results from file
    results_list = list()
    sent_requests = 0
    sent_time = 0
    for filename in filenames:
        f = open(filename + '.bm','r')
        # first line is basic info of slave's work
        result = f.readline()
        result = eval(result.rstrip())
        sent_requests += result[0]
        sent_time += result[1]
        RPS = result[2]
        # following content of file are records of each request
        result = f.readline()
        while (result != ""):
            result = eval(result.rstrip())
            results_list.append(result)
            result = f.readline()
        f.close()

    sent_time /= SlaveNumber
    successful_requests = len(results_list)
    print "Total requests sent: %d, successful requests: %d, time consumed: %f"%(sent_requests, successful_requests, sent_time)

    if successful_requests > 0:
        # Average response time
        response_time = (zip(*results_list))[3]
        print "Average response time: %f s"%(sum(response_time) / float(len(response_time)))

        # Sort request records by sent time
        sorted_results = sorted(results_list, key=lambda result:result[1])
        start_time = results_list[0][1]
        By_request = [[0,0] for i in range(int(math.ceil(sorted_results[successful_requests-1][1]-start_time)) + 1)]
        for result in sorted_results:
            interval = int(math.ceil(result[1] - start_time))-1
            By_request[interval][0] += result[3]
            By_request[interval][1] += 1
        for byreq in By_request:
            if byreq[1]!= 0:
                byreq[0] = round((byreq[0] / byreq[1]), 3) * 1000
        print "By sending time per second: [Response time,  successful requests]"
        print By_request

        # Sort request records by received time
        sorted_results = sorted(results_list, key=lambda result:result[2])
        By_response = [[0,0] for i in range(int(math.ceil(sorted_results[successful_requests-1][2]-start_time)) + 1)]
        for result in sorted_results:
            interval = int(math.ceil(result[2] - start_time))-1
            By_response[interval][0] += result[3]
            By_response[interval][1] += 1
        for byres in By_response:
            if byres[1]!=0:
                byres[0] = round((byres[0] / byres[1]), 3) * 1000
        print "By receving time per second: [Response time,  successful requests]"
        print By_response

        # Plot response time
        fig = plt.figure()
        ax = fig.add_subplot(111)
        line1, = ax.plot(range(len(By_request)),zip(*By_request)[0], color='b')
        line2, = ax.plot(range(len(By_response)),zip(*By_response)[0], color='r')
        ax.set_xlabel('Time (second)')
        ax.set_ylabel('Response Time (millisecond)')
        plt.legend([line1, line2], ['Succ_Requests/sec', 'Succ_Respons/sec'], loc=8, fontsize=8)
        fig.suptitle('Request Per Second: %f'%(sent_requests/sent_time))
        fig.savefig('plot_result/' + str(RPS) + '_RT.png')

        # Plot successful RPS
        fig = plt.figure()
        ax = fig.add_subplot(111)
        line1, = ax.plot(range(len(By_request)),zip(*By_request)[1], color='b')
        line2, = ax.plot(range(len(By_response)),zip(*By_response)[1], color='r')
        ax.set_xlabel('Time (second)')
        ax.set_ylabel('Successful Request/Response')
        plt.legend([line1, line2], ['Succ_Requests/sec', 'Succ_Respons/sec'], loc=8, fontsize=8)
        fig.suptitle('Request Per Second: %f'%(sent_requests/sent_time))
        fig.savefig('plot_result/' + str(RPS) + '_RPS.png')

        # Plot CDF of response time
        fig = plt.figure()
        ax = fig.add_subplot(111)
        sorted_response_time = sorted(response_time)
        sorted_response_time = [x*1000 for x in sorted_response_time]
        print "Response Time(90%): " + str(sorted_response_time[int(0.9 * len(sorted_response_time))])
        y_value = [y / float(len(sorted_response_time)) * 100 for y in range(len(sorted_response_time))]
        line1, = ax.plot(sorted_response_time, y_value, color='b')
        ax.set_xlabel('Response Time (millisecond)',fontsize=18)
        ax.set_ylabel('Percentage (%)',fontsize=18)
        fig.suptitle('Request Per Second: %f'%(sent_requests/sent_time),fontsize=20)
        fig.savefig('plot_result/' + str(RPS) + '_CDF.png')
