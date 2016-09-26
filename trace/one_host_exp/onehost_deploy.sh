#!/bin/bash

# Auto install Kafka and configure the environment
#
# Author: Shijie Sun
# Email: septimus145@gmail.com
# July, 2016

if [[ $UID != 0  ]]; then
    echo "Please run this script with sudo:"
    echo "sudo $0 $*"
    exit 1
fi

kafka_no="1"

# Install editor and jre
sudo apt-get update
sudo apt-get install -y vim tmux
sudo apt-get install -y default-jdk maven
JAVA_HOME=$(sudo update-java-alternatives -l | head -n 1 | cut -f3 -d' ')
echo JAVA_HOME=\"$JAVA_HOME\" | sudo tee --append /etc/environment
export JAVA_HOME=$JAVA_HOME

# Download kafka
kafka_path="/usr/share/kafka/"
wget http://www-eu.apache.org/dist/kafka/0.10.0.0/kafka_2.11-0.10.0.0.tgz
sudo tar -xvzf kafka_2.11-0.10.0.0.tgz -C /usr/share
sudo mv /usr/share/kafka_2.11-0.10.0.0 /usr/share/kafka
rm kafka_2.11-0.10.0.0.tgz

# Configure the zookeeper
cat kafka/zookeeper_append.properties | sudo tee --append $kafka_path/config/zookeeper.properties
server_info="server.1=localhost:2888:3888"
echo $server_info | sudo tee --append $kafka_path/config/zookeeper.properties
sudo mkdir -p /tmp/zookeeper
sudo touch /tmp/zookeeper/myid
echo $kafka_no | sudo tee --append /tmp/zookeeper/myid

# Configure the kafka
znodes="localhost:2181"
sudo sed -i -e "s/\(broker.id=\).*/\1$kafka_no/" \
    -e "s/\(zookeeper.connect=\).*/\1$znodes/" $kafka_path/config/server.properties
echo "delete.topic.enable=true" | sudo tee --append $kafka_path/config/server.properties

# Download the spark
spark_path="/usr/share/spark/"
wget http://www-eu.apache.org/dist/spark/spark-1.6.2/spark-1.6.2-bin-hadoop2.6.tgz
sudo tar -xvzf spark-1.6.2-bin-hadoop2.6.tgz -C /usr/share
sudo mv /usr/share/spark-1.6.2-bin-hadoop2.6 /usr/share/spark
rm spark-1.6.2-bin-hadoop2.6.tgz
echo "spark.io.compression.codec    lzf" | sudo tee --append $spark_path/conf/spark-defaults.conf

# extra config
sudo mkdir -p /var/spark_tmp
sudo cp spark/entry.dat /var/spark_tmp/

# Download the httpd
sudo apt-get install -y apache2 php5 libapache2-mod-php5

# Configure the httpd
sudo cp front_server/player.php /var/www/html
sudo cp front_server/player_EG.php /var/www/html
sudo mkdir /var/www/info
sudo chmod 777 /var/www/info
sudo sed -i -e "s/\(KeepAlive \).*/\1"Off"/" \
    /etc/apache2/apache2.conf
sudo service apache2 reload

# upload traces
sudo apt-get install -y unzip
unzip trace-to-shijie.zip
rm -r __MACOSX trace-to-shijie.zip
sudo chmod -R 777 trace-to-shijie
