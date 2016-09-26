package mybenchmark;

import java.util.Properties;
import java.io.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MsgSender implements Runnable {

    protected boolean isStopped = false;
    protected String brokerList = "";		// list of broker
    protected String hostname = "";		// name of current host
    public KafkaProducer<String, String> producer = null;	// kafka producer
    protected long mps = 1;
    protected long sleep_interval = 1000;
    protected long mploop = 1;
    protected callback1 mycb1 = null;

    public MsgSender(int mps, String hostname, String brokerList) {
        this.hostname = hostname;
        this.brokerList = brokerList;
        this.mps = mps;
        this.sleep_interval = ((long)(this.sleep_interval / mps) < 1)?1:(long)(this.sleep_interval / mps);
        this.mploop = ((mps / 1000) > 1)?(mps / 1000):1;
        System.out.println("------ Here is " + this.hostname + "------");
        System.out.printf("sleep time: %d ms, msg per loop: %d\n", this.sleep_interval, this.mploop);

        // setup producer
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", brokerList);
        producerProps.put("acks", "all");
        producerProps.put("retries", 0);
        producerProps.put("batch.size", 16384); 
        producerProps.put("linger.ms", 1);
        producerProps.put("buffer.memory", 33554432);
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        this.producer = new KafkaProducer<String, String>(producerProps);
    }

    protected class callback1 implements Callback
    {
        private long mps = 0;
        private long sendcount = 0;
        private long timeLast = 0;
        private long timeNow = 0;
        public callback1(long mps) {
            this.mps = mps;
            this.sendcount = 0;
            this.timeLast = System.currentTimeMillis();
        }
        public void onCompletion(RecordMetadata metadata, Exception e) {
            if(e != null)
                e.printStackTrace();
            else {
                sendcount++;
                if (this.sendcount % this.mps == 0) {
                    this.timeNow = System.currentTimeMillis();
                    System.out.printf("Total sent: %d,  Last %d costs %f sec\n", this.sendcount, this.mps, (this.timeNow - this.timeLast)/1000.0);
                    this.timeLast = this.timeNow;
                }
            }
        }
    }
 

    public void run() {
        String msg = "";
        try (BufferedReader br = new BufferedReader(new FileReader("/var/www/msg.dat"))) {
            msg = br.readLine();
        } catch (Exception e) {
            System.err.println("Read file Exception: " + e.getMessage());
        }
        ProducerRecord<String, String> data = new ProducerRecord<>("internal_groups", msg);
        this.mycb1 = new callback1(this.mps);
        while(! isStopped()) {
            for (int i=0;i<this.mploop;i++)
                this.producer.send(data, mycb1); 
            try {
                Thread.sleep(this.sleep_interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
            System.out.println("Usage: java mybenchmark.MsgSender kafka_server mps");
            return;
        }
        String hostname = "HOST";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e){
            hostname = "HOST";
        }
        String brokerList = args[0].replace(",",":9092,") + ":9092";
        Thread sender = new Thread(new MsgSender(Integer.parseInt(args[1]), hostname, brokerList));
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
