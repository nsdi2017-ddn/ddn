package frontend;

import java.util.concurrent.ConcurrentHashMap;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Manage the groups of current cluster
 *
 * Retrive the info of updates from file and send them to Kafka server
 * Fetch group table from Kafka and maintain it
 * Fetch decisions from Kafka
 */

public class GroupManager {

    protected Thread decisionCollector = null;
    protected Thread groupTableUpdater = null;
    protected Thread infoSender = null;
    protected String hostname = "";
    protected String kafkaBrokerList = "";
    protected String clusterID = "";
    public ConcurrentHashMap<String, String> group2ClusterMap = null;

    public GroupManager( String clusterID, String kafkaServerList, String configFile ) {
        this.clusterID = clusterID;
        try {
            this.hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e){
            this.hostname = "HOST";
        }
        this.kafkaBrokerList = kafkaServerList.replace(",",":9092,") + ":9092";
        this.group2ClusterMap = new ConcurrentHashMap<>();

        this.groupTableUpdater = new Thread(new GroupTableUpdater(this.hostname, this.clusterID, this.kafkaBrokerList, this.group2ClusterMap));
        this.groupTableUpdater.setDaemon(true);
        this.groupTableUpdater.start();
        System.out.println("Group table updater ready.");

        this.decisionCollector = new Thread(new DecisionCollector(this.hostname, this.kafkaBrokerList));
        this.decisionCollector.setDaemon(true);
        this.decisionCollector.start();
        System.out.println("Decision collector ready.");

        this.infoSender = new Thread(new InfoSender(this.kafkaBrokerList, this.clusterID, this.group2ClusterMap, configFile));
        this.infoSender.setDaemon(true);
        this.infoSender.start();
        System.out.println("Info sender ready.");
    }

    public static void main( String[] args )
    {
        if (args.length < 3) {
            System.out.println("Usage: java frontend.GroupManager cluster_ID kafka_server config_file");
            System.out.println("\n\tcluster_ID is the ID of current cluster");
            System.out.println("\n\tkafka_server is the list of IP of kafka servers, separated by comma");
            System.out.println("\n\tconfig_file contains labels of update info and reduced labels");
            return;
        }

        GroupManager gManager = new GroupManager(args[0], args[1], args[2]);

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
