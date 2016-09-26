/**
 * Consume messages from one or more topics in Kafka and make decisions.
 *
 * Example:
 *    $ bin/spark-submit --class frontend.DecisionMaker --master local[*] ~/frontend/DMLogic/target/DMLogic-1.0-SNAPSHOT.jar \
 *      broker1-host:port,broker2-host:port topic-in topic-out RPS
 */

package frontend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Properties;
import java.util.Collections;
import java.io.*;

import scala.Tuple2;
import scala.reflect.ClassTag$;
import scala.Option;
import scala.Some;

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
import org.apache.spark.streaming.Time;
import org.apache.spark.streaming.dstream.DStream;
import org.apache.spark.streaming.dstream.ConstantInputDStream;

// for changing logger config
import org.apache.log4j.Logger;
import org.apache.log4j.Level;



public final class DecisionMaker_EG {

  public final static int windowSize = 1; // minutes
  public final static int processInterval = 2; // seconds

  public static void main(String[] args) throws Exception {
    if (args.length < 5) {
      System.err.println("Usage: DecisionMaker <brokers> <topic-in> <topic-out> <epsilon> <RPS>\n" +
          "  <brokers> is a list of one or more Kafka brokers\n" +
          "  <topic-in> is the kafka topic to consume from\n" +
          "  <topic-out> is the kafka topic to publish the decision to\n" +
          "  <epsilon> is the parameter of epsilon greedy algorithm\n" +
          "  <RPS> is the RPS to preload history data of sliding window\n");
      System.exit(1);
    }

    Logger.getLogger("org").setLevel(Level.OFF);
    Logger.getLogger("akka").setLevel(Level.OFF);

    // parse the arguments
    final String brokers = args[0];
    String topicIn = args[1];
    final String topicOut = args[2];
    final double epsilon = Double.parseDouble(args[3]);
    int RPS = Integer.parseInt(args[4]);

    // setup producer
    final Properties producerProps = new Properties();
    producerProps.put("bootstrap.servers", brokers);
    producerProps.put("acks", "all");
    producerProps.put("retries", 0);
    producerProps.put("batch.size", 16384);
    producerProps.put("linger.ms", 1);
    producerProps.put("buffer.memory", 33554432);
    producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    // Create context with a 1 seconds batch interval
    SparkConf sparkConf = new SparkConf().setAppName("DicisionMaker");
    final JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, Durations.milliseconds(1000));

    // Create direct kafka stream with brokers and topic
    Set<String> topicsSet = new HashSet<>(Arrays.asList(topicIn));
    Map<String, String> kafkaParams = new HashMap<>();
    kafkaParams.put("metadata.broker.list", brokers);
    JavaPairInputDStream<String, String> messages = KafkaUtils.createDirectStream(
      jssc,
      String.class,
      String.class,
      StringDecoder.class,
      StringDecoder.class,
      kafkaParams,
      topicsSet
    );

    // Load history data
    String postData = "";
    try (BufferedReader br = new BufferedReader(new FileReader("/var/spark_tmp/entry.dat"))) {
      postData = br.readLine();
    } catch (Exception e) {
      System.err.println("Load data exception: " + e.getMessage());
    }
    List<Tuple2<String, String>> historyData = Collections.nCopies(RPS * 60, new Tuple2<String,String>("history", postData)); // load one minute's history data per second
    final JavaRDD<Tuple2<String, String>> historyDData = jssc.sparkContext().parallelize(historyData);

    // Create history data stream
    List<Tuple2<String, String>> emptyData = Collections.nCopies(0, new Tuple2<String,String>("history", postData));
    final JavaRDD<Tuple2<String, String>> emptyDData = jssc.sparkContext().parallelize(emptyData);
    final long startTime = System.currentTimeMillis();
    final ConstantInputDStream<Tuple2<String, String>> historyInputDStream = new ConstantInputDStream<Tuple2<String, String>>(jssc.ssc(), historyDData.rdd(), ClassTag$.MODULE$.<Tuple2<String, String>>apply(new Tuple2<String, String>("", "").getClass())) {
      @Override
      public scala.Option<RDD<Tuple2<String, String>>> compute(Time validTime) {
        if (validTime.milliseconds() > startTime && validTime.milliseconds() < startTime + windowSize * 1000 + 100) // 500 is a compensation for pre-processing time
          return Some.apply(historyDData.rdd());
        else
          return Some.apply(emptyDData.rdd());
      }
    };
    DStream<Tuple2<String, String>> historyDStream = historyInputDStream;
    JavaPairDStream<String, String> historyPairDStream = new JavaPairDStream(historyDStream, ClassTag$.MODULE$.apply("".getClass()), ClassTag$.MODULE$.apply("".getClass()));

    // map to pair to retrieve the data and group_id
    // then reduce by key to combine the performance of each decision within sliding window
    JavaPairDStream<String, Map<String, double[]>> qualitySums = messages.union(historyPairDStream).mapToPair(
      new PairFunction<Tuple2<String, String>, String, Map<String, double[]>>() {
        @Override
        public Tuple2<String, Map<String, double[]>> call(Tuple2<String, String> tuple2) {
          JSONObject jObject = new JSONObject(tuple2._2().trim());
          String group_id = jObject.getString("group_id");
          String[] updates = jObject.getString("update").split("\t");
          String decision = updates[0];
          double score = 0 - Double.parseDouble(updates[1]);
          Map<String, double[]> info = new HashMap<String, double[]>();
          info.put(decision, new double[]{score,1});
          return new Tuple2<>(group_id, info);
        }
      }).reduceByKeyAndWindow(
        new Function2<Map<String, double[]>, Map<String, double[]>, Map<String, double[]>>() {
        @Override
        public Map<String, double[]> call(Map<String, double[]> m1, Map<String, double[]> m2) {
            // iterate map1 and merge it to map2
            Set<Map.Entry<String, double[]>> m1Entries = m1.entrySet();
            for (Map.Entry<String, double[]> m1Entry : m1Entries) {
                double[] m2Value = m2.get(m1Entry.getKey());
                double[] m1Value = m1Entry.getValue();
                if (m2Value == null) {
                    m2.put(m1Entry.getKey(), m1Value);
                }
                else {
                    m2Value[0] += m1Value[0];
                    m2Value[1] += m1Value[1];
                }
            }
          return m2;
        }
      }, Durations.minutes(windowSize), Durations.seconds(processInterval));  //func, windowlength, slideinterval

    // put the result to kafka broker
    qualitySums.foreachRDD(new VoidFunction<JavaPairRDD<String, Map<String, double[]>>>() {
        @Override
        public void call(JavaPairRDD<String, Map<String, double[]>> groups) throws Exception {
            // foreach group
            groups.foreach(new VoidFunction<Tuple2<String, Map<String, double[]>>> () {
                @Override
                public void call(Tuple2<String, Map<String, double[]>> group) throws Exception {
                    // select best decision and put other decisions to a json array
                    double bestScore = -Double.MAX_VALUE;
                    String bestDecision = null;
                    JSONArray jArray = new JSONArray();
                    for (Map.Entry<String, double[]> entry : group._2().entrySet()) {
                        if (entry.getValue()[0] / entry.getValue()[1] > bestScore) {
                            if (bestDecision != null)
                                jArray.put(bestDecision);
                            bestDecision = entry.getKey();
                            bestScore = entry.getValue()[0] / entry.getValue()[1];
                        } else {
                            jArray.put(entry.getKey());
                        }
                    }
                    // generate the result and sent it to kafka server
                    JSONObject jObject = new JSONObject();
                    jObject.put("random", jArray);
                    jObject.put("best", bestDecision);
                    jObject.put("epsilon", epsilon);
                    ProducerRecord<String, String> data = new ProducerRecord<>(topicOut, group._1() + ";" + jObject.toString() + ";From: " + brokers);
                    KafkaProducer<String, String> kproducer = new KafkaProducer<String, String>(producerProps);
                    kproducer.send(data);
            }});
      }});

    // Start the computation
    jssc.start();
    jssc.awaitTermination();
  }
}
