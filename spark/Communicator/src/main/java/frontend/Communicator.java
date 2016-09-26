/**
 * Upload updates to backend and communicate with other cluster
 */

package frontend;

import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;

import scala.Tuple2;

import org.json.JSONObject;
import org.json.JSONArray;

import kafka.serializer.StringDecoder;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.apache.spark.SparkConf;
import org.apache.spark.rdd.RDD;
import org.apache.spark.api.java.function.*;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.streaming.api.java.*;
import org.apache.spark.streaming.kafka.*;
import org.apache.spark.streaming.Durations;

// for changing logger config
import org.apache.log4j.Logger;
import org.apache.log4j.Level;



public final class Communicator {

    public final static int processInterval = 1; // seconds

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: Communicator config_file");
            System.exit(1);
        }

        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);

        // basic configuration
        final Properties config = new Properties();
        InputStream iStream = null;
        try {
            iStream = new FileInputStream(args[0]);
            config.load(iStream);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (iStream != null) {
                try {
                    iStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        final String currentClusterID = config.getProperty("clusterID");
        final String updateTopic = config.getProperty("updateTopic");
        final String uploadTopic = config.getProperty("uploadTopic");
        final String decisionTopic = config.getProperty("decisionTopic");
        final String subscribeTopic = config.getProperty("subscribeTopic");
        final String forwardTopic = config.getProperty("forwardTopic");
        final String sampleTopic = config.getProperty("sampleTopic");
        final String aliveTopic = config.getProperty("aliveTopic");
        final int managementLabelsNum = Integer.valueOf(config.getProperty("managementLabelsNum"));

        // setup producer basic config
        final Properties producerProps = new Properties();
        producerProps.put("acks", "all");
        producerProps.put("retries", 0);
        producerProps.put("batch.size", 16384);
        producerProps.put("linger.ms", 1);
        producerProps.put("buffer.memory", 33554432);
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // create streaming context
        SparkConf sparkConf = new SparkConf().setAppName("Communicator");
        final JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, Durations.seconds(processInterval));

        // create a rdd to store the group-subscribers map
        List<Tuple2<String, HashSet<String>>> groupSubscriberList = new ArrayList<>();
        JavaRDD<Tuple2<String, HashSet<String>>> groupSubscriberRDD = jssc.sparkContext().parallelize(groupSubscriberList);
        JavaPairRDD<String, HashSet<String>> gsRDD = JavaPairRDD.fromJavaRDD(groupSubscriberRDD);
        // using queue to make the map updatable
        final ConcurrentLinkedQueue<JavaPairRDD<String, HashSet<String>>> gsQueue = new ConcurrentLinkedQueue<>();
        gsQueue.add(gsRDD);

        Map<String, String> kafkaParams = new HashMap<>();
        kafkaParams.put("metadata.broker.list", config.getProperty(currentClusterID));
        // Create stream of upload topic
        Set<String> uploadTopicSet = new HashSet<>(Arrays.asList(uploadTopic));
        JavaPairInputDStream<String, String> uploadMsgs = KafkaUtils.createDirectStream(
            jssc,
            String.class,
            String.class,
            StringDecoder.class,
            StringDecoder.class,
            kafkaParams,
            uploadTopicSet
        );
        // Create stream of decision topic
        Set<String> decisionTopicSet = new HashSet<>(Arrays.asList(decisionTopic));
        JavaPairInputDStream<String, String> decisionMsgs = KafkaUtils.createDirectStream(
            jssc,
            String.class,
            String.class,
            StringDecoder.class,
            StringDecoder.class,
            kafkaParams,
            decisionTopicSet
        );
        // Create stream of subscribe topic
        Set<String> subscribeTopicSet = new HashSet<>(Arrays.asList(subscribeTopic));
        JavaPairInputDStream<String, String> subscribeMsgs = KafkaUtils.createDirectStream(
            jssc,
            String.class,
            String.class,
            StringDecoder.class,
            StringDecoder.class,
            kafkaParams,
            subscribeTopicSet
        );
        // Create stream of forward topic
        Set<String> forwardTopicSet = new HashSet<>(Arrays.asList(forwardTopic));
        JavaPairInputDStream<String, String> forwardMsgs = KafkaUtils.createDirectStream(
            jssc,
            String.class,
            String.class,
            StringDecoder.class,
            StringDecoder.class,
            kafkaParams,
            forwardTopicSet
        );

        // upload all the updates
        uploadMsgs.foreachRDD(new VoidFunction<JavaPairRDD<String, String>>() {
            // foreachRDD will get RDD of each batch of dstream
            @Override
            public void call(JavaPairRDD<String, String> uploadMsgsRDD) throws Exception {
                uploadMsgsRDD.sample(false, 0.01).flatMapToPair(new PairFlatMapFunction<Tuple2<String, String>, String, Integer>() {
                    @Override
                    public Iterable<Tuple2<String, Integer>> call(Tuple2<String, String> tuple2) {
                        List<Tuple2<String, Integer>> result = new ArrayList<>();
                        String[] features = tuple2._2().split("\t");
                        for (int i = 0; i < (features.length - managementLabelsNum); i++) {
                            result.add(new Tuple2<>(String.valueOf(i) + ";" + features[i], 1));
                        }
                        return result;
                    }
                }).reduceByKey(new Function2<Integer, Integer, Integer>() {
                    @Override
                    public Integer call(Integer i1, Integer i2){
                        return i1+i2;
                    }
                }).foreachPartition(new VoidFunction<Iterator<Tuple2<String, Integer>>> () {
                    @Override
                    public void call(Iterator<Tuple2<String, Integer>> samples_iter) throws Exception {
                        producerProps.put("bootstrap.servers", config.getProperty("backendBrokers"));
                        KafkaProducer<String, String> kproducer = new KafkaProducer<String, String>(producerProps);
                        ProducerRecord<String, String> data = null;
                        Tuple2<String, Integer> sample = null;
                        while (samples_iter.hasNext()) {
                            sample = samples_iter.next();
                            data = new ProducerRecord<>(sampleTopic, sample._1() + ";" + String.valueOf(sample._2()));
                            kproducer.send(data);
                        }
                    }
                });
                uploadMsgsRDD.foreachPartition(new VoidFunction<Iterator<Tuple2<String, String>>> () {
                    @Override
                    public void call(Iterator<Tuple2<String, String>> updates_iter) throws Exception {
                        producerProps.put("bootstrap.servers", config.getProperty("backendBrokers"));
                        KafkaProducer<String, String> kproducer = new KafkaProducer<String, String>(producerProps);
                        ProducerRecord<String, String> data = null;
                        Tuple2<String, String> update = null;
                        while (updates_iter.hasNext()) {
                            update = updates_iter.next();
                            data = new ProducerRecord<>(uploadTopic, update._2());
                            kproducer.send(data);
                        }
                        data = new ProducerRecord<>(aliveTopic, currentClusterID);
                        kproducer.send(data);
                    }
                });
            }
        });

        // push all the decision to subscribers
        decisionMsgs.foreachRDD(new VoidFunction<JavaPairRDD<String, String>>() {
            // foreachRDD will get RDD of each batch of dstream
            @Override
            public void call(JavaPairRDD<String, String> decisionMsgsRDD) throws Exception {
                // get the group-subscriber map
                // collectAsMap has a bug when used by rdd which is not from jssc.parallel directly
                // so here need to use collect and convert it to map manually
                List<Tuple2<String, HashSet<String>>> gsList = gsQueue.peek().collect();
                final Map<String, HashSet<String>> groupSubscriber = new HashMap<>();
                for (Tuple2<String, HashSet<String>> gs : gsList) {
                    groupSubscriber.put(gs._1(), gs._2());
                }
                // push the decisions
                decisionMsgsRDD.foreachPartition(new VoidFunction<Iterator<Tuple2<String, String>>> () {
                    @Override
                    public void call(Iterator<Tuple2<String, String>> decisions_iter) throws Exception {
                        ConcurrentHashMap<String, KafkaProducer<String, String>> producerMap = new ConcurrentHashMap<>();
                        Tuple2<String, String> decision = null;
                        KafkaProducer<String, String> kproducer = null;
                        ProducerRecord<String, String> data = null;
                        String groupID = null;
                        String clusterID = null;
                        while (decisions_iter.hasNext()) {
                            decision = decisions_iter.next();
                            groupID = decision._2().split(";")[0];
                            // foreach subscriber
                            if (groupSubscriber.containsKey(groupID)) {
                                Iterator<String> it = groupSubscriber.get(groupID).iterator();
                                while (it.hasNext()) {
                                    clusterID = it.next();
                                    // if it is not current cluster
                                    if (clusterID.equals(currentClusterID))
                                        continue;
                                    // if do not have producer for this cluster, create one
                                    if (! producerMap.containsKey(clusterID)) {
                                        producerProps.put("bootstrap.servers", config.getProperty(clusterID));
                                        kproducer = new KafkaProducer<String, String>(producerProps);
                                        producerMap.put(clusterID, kproducer);
                                    }
                                    // push the decision
                                    data = new ProducerRecord<>(decisionTopic, decision._2());
                                    producerMap.get(clusterID).send(data);
                                }
                            }
                        }
                    }
                });
            }
        });

        // update the subscriber of groups
        subscribeMsgs.mapToPair(new PairFunction<Tuple2<String, String>, String, String>() {
            @Override
            public Tuple2<String, String> call(Tuple2<String, String> tuple2) {
                return new Tuple2<>(tuple2._2().split(";")[0], tuple2._2().split(";")[1]);
            }
        }).foreachRDD(new VoidFunction<JavaPairRDD<String, String>>() {
            // foreachRDD will get RDD of each batch of dstream
            @Override
            public void call(JavaPairRDD<String, String> subscribeMsgsRDD) throws Exception {
                JavaPairRDD<String, HashSet<String>> newgsRDD = subscribeMsgsRDD.cogroup(gsQueue.peek()).mapToPair(
                        new PairFunction<Tuple2<String, Tuple2<Iterable<String>, Iterable<HashSet<String>>>>, String, HashSet<String>>() {
                            @Override
                            public Tuple2<String, HashSet<String>> call(Tuple2<String, Tuple2<Iterable<String>, Iterable<HashSet<String>>>> tuple2) {
                                Iterator<String> iter1 = tuple2._2()._1().iterator();
                                Iterator<HashSet<String>> iter2 = tuple2._2()._2().iterator();
                                HashSet<String> subscribers = null;
                                String subscribe = null;
                                if (iter2.hasNext())
                                    subscribers = iter2.next();
                                else
                                    subscribers = new HashSet<>();
                                while (iter1.hasNext()) {
                                    subscribe = iter1.next();
                                    if (! subscribers.contains(subscribe))
                                        subscribers.add(subscribe);
                                }
                                return new Tuple2(tuple2._1(), subscribers);
                            }
                        }
                        );
                //System.out.println(newgsRDD.collect());

                // use peek above and first add then poll here
                // this is to make sure there is at least one rdd in the queue
                // so other stream can always get the rdd through queue.peek()
                gsQueue.add(newgsRDD);
                gsQueue.poll();
            }
        });


        // forward the updates of external groups
        forwardMsgs.foreachRDD(new VoidFunction<JavaPairRDD<String, String>>() {
            // foreachRDD will get RDD of each batch of dstream
            @Override
            public void call(JavaPairRDD<String, String> forwardMsgsRDD) throws Exception {
                forwardMsgsRDD.foreachPartition(new VoidFunction<Iterator<Tuple2<String, String>>> () {
                    @Override
                    public void call(Iterator<Tuple2<String, String>> forwards_iter) throws Exception {
                        ConcurrentHashMap<String, KafkaProducer<String, String>> producerMap = new ConcurrentHashMap<>();
                        Tuple2<String, String> forward = null;
                        KafkaProducer<String, String> kproducer = null;
                        ProducerRecord<String, String> data = null;
                        String groupID = null;
                        String clusterID = null;
                        // to store all the group_ids and their cluster_ids
                        Map<String, String> groupSubs = new HashMap<String, String>();
                        while (forwards_iter.hasNext()) {
                            forward = forwards_iter.next();
                            JSONObject jObject = new JSONObject(forward._2());
                            clusterID = jObject.getString("cluster_id");
                            groupID = jObject.getString("group_id");
                            // foreach subscriber
                            if (! groupSubs.containsKey(groupID))
                                groupSubs.put(groupID, clusterID);
                            if (! producerMap.containsKey(clusterID)) {
                                producerProps.put("bootstrap.servers", config.getProperty(clusterID));
                                kproducer = new KafkaProducer<String, String>(producerProps);
                                producerMap.put(clusterID, kproducer);
                            }
                            data = new ProducerRecord<>(updateTopic, forward._2());
                            producerMap.get(clusterID).send(data);
                        }
                        // subscribe all the sent groups
                        for (Map.Entry<String, String> gsEntry : groupSubs.entrySet()) {
                            data = new ProducerRecord<>(subscribeTopic, gsEntry.getKey() + ";" + currentClusterID);
                            producerMap.get(gsEntry.getValue()).send(data);
                        }
                    }
                });
            }
        });

        // Start the computation
        jssc.start();
        jssc.awaitTermination();
    }
}
