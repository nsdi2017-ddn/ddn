package mybenchmark;

import java.util.Properties;
import java.util.Arrays;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MsgReader implements Runnable {

    protected boolean isStopped = false;
    protected String brokerList = "";		// list of broker
    protected String topic = "";
    public KafkaConsumer<String, String> consumer = null;       // kafka consumer

    public MsgReader(String topic, String brokerList) {
        this.topic = topic;
        this.brokerList = brokerList;
        // setup consumer
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", brokerList);
        consumerProps.put("group.id", "hhhhhh");
        consumerProps.put("enable.auto.commit", "true");
        consumerProps.put("auto.commit.interval.ms", "1000");
        consumerProps.put("session.timeout.ms", "30000");
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        this.consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Arrays.asList(this.topic));
    }

    public void run() {
        long timeLast = System.currentTimeMillis();
        long time_average = 0;
        int cnt = 0;
        // int rvalue = 0;
        // int MPS = 0;
        while(! isStopped()) {
            ConsumerRecords<String, String> records = consumer.poll(1000);
            for (ConsumerRecord<String, String> record : records) {
                // rvalue = Integer.parseInt((record.value().trim().split(" "))[2]);
                // System.out.printf("offset = %d, time = %.3f, value = %s\n", record.offset(), (record.timestamp() - timeLast)/1000.0, rvalue);
                System.out.printf("offset = %d, time = %.3f\n", record.offset(), (record.timestamp() - timeLast)/1000.0);
                time_average += (record.timestamp() - timeLast);
                // MPS += rvalue;
                cnt++;
                if (cnt >= 10) {
                    // System.out.printf("-------- average-time = %.3f, average MPS = %d --------\n", time_average/10000.0, MPS/8500);
                    System.out.printf("-------- average-time = %.3f --------\n", time_average/10000.0);
                    cnt = 0;
                    // MPS = 0;
                    time_average = 0;
                }
                timeLast = record.timestamp();
            }
        }
        System.out.println("Server Stopped.");
    }

    private synchronized boolean isStopped() {
        return this.isStopped;
    }

    public static void main( String[] args )
    {
        if (args.length < 2) {
            System.out.println("Usage: java mybenchmark.MsgReader kafka_server topic");
            return;
        }
        String brokerList = args[0].replace(",",":9092,") + ":9092";
        Thread sender = new Thread(new MsgReader(args[1], brokerList));
        sender.setDaemon(true);
        sender.start();
        while (true)
        {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // code for stopping current task so thread stops
            }
        }
    }
}
