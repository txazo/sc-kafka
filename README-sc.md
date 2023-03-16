# Kafka本地源码编译启动

* 启动zookeeper
* config/server.properties复制一份，按需修改
* config/log4j.properties复制到core/src/main/resources目录下，替换其中的${kafka.logs.dir}
* 修改build.gradle，`project(':core')`下`compileOnly libs.log4j`修改为下面配置

```shell
implementation libs.slf4jApi
implementation libs.slf4jlog4j
implementation libs.log4j
```

* 运行core/src/main/scala/kafka/Kafka，第一次运行报错，配置`Edit Configurations`，运行参数带上server.properties的路径
* 再次运行core/src/main/scala/kafka/Kafka，不报错，成功打印日志，启动成功
