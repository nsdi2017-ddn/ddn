Codes for DDN's frontend cluster.

# Table of Contents
1. [Environment](#Environment)
2. [Front Server](#frontserver)
	1. [Web Server](#webserver)
	2. [Group Manager](#groupmanager)
3. [kafka](#kafka)
4. [spark](#spark)
	1. [Decision Maker](#decisionmaker)
	2. [Communicator](#communicator)
5. [Benchmark](#benchmark)
	1. [Response Time](#responsetime)
		1. [python Script](#pythonscript)
		2. [Apache Benchmark](#apachebenchmark)
	2. [Python Benchmark](#pythonbenchmark)
		1. [Standalone Benchmark](#standalonebenchmark)
		2. [Distributed Benchmark](#distributedbenchmark)
	3. [Kafka Benchmark](#kafkabenchmark)
6. [Trace](#trace)
	1. [Algorithm Comparison](#algorithmcomparison)
	2. [Fault Tolerance](#faulttolerance)
	3. [One Host Experiment](#onehostexperiment)
7. [Abandoned](#abandoned)


##Environment <a name="environment"></a>

System: Ubuntu 15.10

Java compiler tools ([Maven](https://maven.apache.org/)) installation:

```sh
$ sudo apt-get update
```

```sh
$ sudo apt-get install -y default-jdk maven
```

---

##Front Server <a name="frontserver"></A>

Contains programs need to be deployed on each front-end server host.

###Web Server <a name="webserver"></A>

Auto-deployment script (for Apache httpd and php programs):

```sh
../front_server $ sudo ./frontserver_deploy.sh
```
###Group Manager <a name="groupmanager"></A>

compile using maven:

```sh
../GroupManager $ mvn package
```

run:

```sh
../GroupManager $ java -cp target/GroupManager-1.0-SNAPSHOT.jar frontend.GroupManager <cluster_ID> <kafka_server> <config_file>
```

	<cluster_ID> is the ID of current cluster

	<kafka_server> is the list of IP of kafka servers, separated by comma

	<config_file> contains labels of update info and reduced labels

---

##Kafka <a name="kafka"></A>

Deploy on one or more hosts in each cluster to manage the communications between each functional module.

[Kafka](http://kafka.apache.org/) deployment:

```sh
../kafka $ sudo ./kafka_deploy.sh <host_list> <host_number>
```

	<host_list> is all IP addresses of kafka servers, separated by comma
	
	<host_number> is the sequence number of current host in host_list

run: 

```sh
$ cd /usr/share/kafka
```

```sh
$ sudo bin/zookeeper-server-start.sh config/zookeeper.properties &
```

```sh
$ sudo bin/kafka-server-start.sh config/server.properties
```

**Note**:
If run kafka on more than one host. Execute third command only if second command has been executed on each host.

---

##Spark <a name="spark"></A>

Contains Decision-making module and communication module， each uses spark and can be run on one or more hosts.

[Spark](https://spark.apache.org/) deployment:

```sh
../spark $ sudo ./spark_deploy.sh
```

###Decision Maker <a name="decisionmaker"></A>

make decision for each group.

compile using maven and submit it to spark.


###Communicator <a name="communicator"></A>

communicate with backend cluster and other frontend clusters.

like `DecisionMaker`, compile using maven and submit it to spark.


**reference :**

[Run Spark on Multi-hosts](http://spark.apache.org/docs/latest/spark-standalone.html)

[Spark Submitting Applications](http://spark.apache.org/docs/latest/submitting-applications.html)


---

##Benchmark <a name="benchmark"></A>

some small scripts and programs to test the scalability of frontend cluster.


###Response Time <a name="responsetime"></A>

Test the response time of requests.

#### Python Script <a name="pythonscript"></A>

A simple python program to perform HTTP POST request 1000 times and plot the CDF of response time:

```sh
$ ./post_time.py
```
#### Apache Benchmark <a name="apachebenchmark"></A>

A shell using [Apache Benchmark](https://httpd.apache.org/docs/2.4/programs/ab.html) to test the response time of frontend server.

```sh
$ ./responseTime.ssh
```

###Python Benchmark <a name="pythonbenchmark"></A>

#### Standalone Benchmark <a name="standalonebenchmark"></A>
A standalone benchmark to perform the HTTP POST request.
Test time and request per second(RPS) can be controlled.

```sh
$ ./benchmark.py
```

#### Distributed Benchmark <a name="distributedbenchmark"></A>
A distributed benchmark to perform the HTTP POST request.

Run slave program on all the hosts to perform the benchmark. 
Then run master program on one host to start test.
When test finished, master program will generator three figures(Response Time, Successful RPS, CDF Response Time)


run slave:

```sh
$ ./dbenchmark_slave <url>
```

	<url>: Desti-URL slave program will send requests to

run master:

```sh
$ ./dbenchmark_master <Time> <RPS>
```

	<Time>: the time this test will last
	
	<RPS>: request per second. Actually this parameter is only positive correlated with real RPS. The real RPS will show in the result figure.

**Note**: the host runs master program need to install `matplotlib` :

```sh
sudo apt-get install -y python-matplotlib
```

###Kafka Benchmark <a name="kafkabenchmark"></A>

This is special designed for test of throughput of Kafka and Spark Streaming. Need cooperation of special msg format.

compile:

```sh
../KafkaBenchmark $ mvn package
```

run:

**send msg to kafka :**

```sh
java -cp target/KafkaBenchmark-1.0-SNAPSHOT.jar mybenchmark.MsgReader <kafka_sender> <mps>
```

	<kafka_server>: hostname of kafka server

	mps: messages per second
	
**Note**: By default all msgs are sent to topic `internal_groups`

**receive msg from kafka :**

```sh
java -cp target/KafkaBenchmark-1.0-SNAPSHOT.jar mybenchmark.MsgReader <kafka_server> <topic>
```
	
	<kafka_server>: hostname of kafka server

	<topic>: Kafka topic this Reader will comsume
	
---
##Trace <a name="trace"></A>

some scripts to test the system or algorithm performance using traces.

`trace_sort.sh` : sort the trace by timestamp 

###Algorithm Comparison <a name="algorithmcomparison"></A>

main scripts for algorithm comparison

`auto_plot.sh` : plot the algorithm comparison results

`combine.py` : process raw data

`cost.conf` : [Gnuplot](http://gnuplot.info/) script for the plot

`pull*.sh` : pull the test result from cluster to localhost

`trace_parser.py` : parse the trace and simulate the player

###Fault Tolerance <a name="faulttolerance"></A>

main scripts for fault tolerance experiment

`ft.conf` : Gnuplot script

`sort` : process raw data

`trace_parser_multi.py` : parse the trace and simulate multiple players

###One Host Experiment <a name="onehostexperiment"></A>

For real-world trace benchmark, deploy all mudules of a frontend cluster on one host. This is more efficient for multiple algorithms comparison.

`autoscp.sh` : upload files used.

`onehost_deploy` : deployment script

`start_tmux` : run necessary programs in tmux

---
##Abandoned <a name="abandoned"></A>

abandoned module codes, including load balancer ([HAProxy](http://www.haproxy.org/)) and proxy server.


