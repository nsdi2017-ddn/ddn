package frontend;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Fetch decisions from Kafka
 */


public class DecisionCollector_EG implements Runnable {

    protected String brokerList = "";		// list of broker
    protected String hostname = "";		// name of current host
    public KafkaConsumer<String, String> consumer = null;       // kafka consumer

    public DecisionCollector_EG( String hostname, String brokerList ) {
        this.hostname = hostname;
        this.brokerList = brokerList;
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
        consumer.subscribe(Arrays.asList("decision"));
    }

    public void run() {
        KafkaConsumer<String, String> tconsumer = consumer;
        while (true) {
            ConsumerRecords<String, String> records = tconsumer.poll(1000);
            for (ConsumerRecord<String, String> record : records) {
                String[] decision = record.value().split(";");
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/var/www/info/d_" + decision[0]), "utf-8"))) {
                    JSONObject jObject = new JSONObject(decision[1]);
                    writer.write(String.valueOf(jObject.getDouble("epsilon")));
                    writer.newLine();
                    writer.write(jObject.getString("best"));
                    JSONArray jArray = jObject.getJSONArray("random");
                    for (int i=0; i < jArray.length(); i++) {
                         writer.newLine();
                         writer.write(jArray.getString(i));
                    }
                } catch (Exception e) {
                    System.err.println("Caught Exception: " + e.getMessage());
                }
            }
            try {
                Thread.sleep(1000);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
