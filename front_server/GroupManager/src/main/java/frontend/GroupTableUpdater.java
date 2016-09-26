package frontend;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Fetch group table from Kafka and maintain it
 */


public class GroupTableUpdater implements Runnable {

    protected String brokerList = "";		// list of broker
    protected String hostname = "";		// name of current host
    public KafkaConsumer<String, String> consumer = null;       // kafka consumer
    public ConcurrentHashMap<String, String> group2ClusterMap = null;

    public GroupTableUpdater( String hostname, String clusterID, String brokerList, ConcurrentHashMap<String, String> group2ClusterMap ) {
        this.hostname = hostname;
        this.brokerList = brokerList;
        this.group2ClusterMap = group2ClusterMap;
        group2ClusterMap.put("null", clusterID);
        // setup consumer
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", brokerList);
        consumerProps.put("group.id", this.hostname);
        consumerProps.put("enable.auto.commit", "true");
        consumerProps.put("auto.commit.interval.ms", "1000");
        consumerProps.put("session.timeout.ms", "30000");
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        this.consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Arrays.asList("group_table"));
    }

    public void run() {
        while (true) {
            ConsumerRecords<String, String> records = this.consumer.poll(1000);
            for (ConsumerRecord<String, String> record : records) {
                JSONObject jObject = new JSONObject(record.value());

                // if it is group to cluster map, save it
                if (jObject.has("GroupAssignment")) {
                    JSONArray jArray = jObject.getJSONArray("GroupAssignment");
                    for (int i = 0; i < jArray.length(); i++) {
                        group2ClusterMap.put(jArray.getJSONObject(i).getString("GroupName"), jArray.getJSONObject(i).getString("Cluster"));
                    }
                }

                // if it is feature values to group map, generate new PHP code
                if (jObject.has("GroupingRules")) {
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/var/www/info/match.php"), "utf-8"))) {
                        JSONObject jObjectRules = jObject.getJSONObject("GroupingRules");
                        ArrayList<String> code = phpGenerator(jObjectRules);
                        Iterator<String> codeIter = code.iterator();
                        while (codeIter.hasNext()) {
                            writer.write(codeIter.next());
                            writer.newLine();
                        }
                    } catch (Exception e) {
                        System.err.println("Caught Exception: " + e.getMessage());
                    }
                }
            }
            try {
                Thread.sleep(1000);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ArrayList<String> phpGenerator (JSONObject jObjectRules) {
        ArrayList<String> code = new ArrayList<String>();
        code.add("<?php");
        code.add("// Match the request into groups, called by update.php");
        code.add("//");
        code.add("// Author: Shijie Sun");
        code.add("// Email: septimus145@gmail.com");
        code.add("// August, 2016");
        code.add("");
        code.add("// Hash the features and find the match");
        ruleParser(jObjectRules, code, 0);
        code.add("?>");
        return code;
    }

    private void ruleParser (JSONObject jObjectRules, ArrayList<String> code, int indent) {
        String indentStr = new String(new char[indent]).replace('\0', ' ');
        if (jObjectRules.has("Field")) {
            JSONArray jArrayTable = jObjectRules.getJSONArray("Table");
            String field = jObjectRules.getString("Field");
            for (int i = 0; i < jArrayTable.length(); i++) {
                JSONObject jObjectRule = jArrayTable.getJSONObject(i);
                code.add(indentStr + "if ($features[" + field + "] == \"" + jObjectRule.getString("Key") + "\") {");
                ruleParser(jObjectRule.getJSONObject("Rule"), code, indent+2);
                code.add(indentStr + "}");
            }
        } else if (jObjectRules.has("GroupName")) {
            code.add(indentStr + "$group_id = \"" + jObjectRules.getString("GroupName") + "\";");
        }
    }
}
