#/bin/bash
ps -ef | grep 'Gradle' | grep -v grep | awk '{print $2}' | xargs kill -9
ps -ef | grep 'kafka' | grep -v grep | awk '{print $2}' | xargs kill -9
ps -ef | grep 'zookeeper' | grep -v grep | awk '{print $2}' | xargs kill -9

# cd ~/JavaProject/sc-kafka
# gradle build -x test

# cd ~/Software/zookeeper/apache-zookeeper-3.8.1-bin/
# bin/zkServer.sh start conf/zoo.cfg

# cd ~/Software/kafka/kafka_2.13-3.4.0
# bin/kafka-topics.sh --create --topic my-kafka-topic-test-012 --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
