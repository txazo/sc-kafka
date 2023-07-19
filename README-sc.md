# Kafka本地源码编译启动

gradle编译: `gradle build`、`gradle build -x test`

* config/server.properties复制一份，按需修改
* config/log4j.properties复制到core/src/main/resources目录下，替换其中的${kafka.logs.dir}
* 修改build.gradle，`project(':core')`下`compileOnly libs.log4j`修改为下面配置

```shell
implementation libs.slf4jApi
implementation libs.slf4jlog4j
implementation libs.log4j
```

* 启动zookeeper

```shell
bin/zkServer.sh start conf/zoo.cfg
```

* 运行core/src/main/scala/kafka/Kafka，第一次运行报错，配置`Edit Configurations`，运行参数带上server.properties的路径
* 再次运行core/src/main/scala/kafka/Kafka，不报错，成功打印日志，启动成功

## 核心类

* kafka.Kafka.main()
* AdminZkClient

```shell
/consumers
/brokers/ids
/brokers/topics
/brokers/seqid
/config/changes
/admin/delete_topics
/isr_change_notification
/latest_producer_id_block
/log_dir_event_notification
/config/topics
/config/clients
/config/users
/config/brokers
/config/ips
```

* KafkaScheduler: 线程池调度器

* LogManager
  * startup()
    * 扫描TopicPartition目录
    * 循环TopicPartition目录，打开所有的LogSegment
    * LocalLog（LogSegment序列）
    * startupWithConfigOverrides
    * LogCleaner

* MetadataCache

* FinalizedFeatureChangeListener

* BrokerToControllerChannelManager: 请求Controller
  * start()

* SocketServer

* AlterPartitionManager
  * start()

* ReplicaManager: 副本管理器
  * startup()

* Broker注册ZK

* KafkaController
  * startup()

* GroupCoordinator: 分组协调器

* FetchManager

## 请求处理

* KafkaRequestHandler
* 校验ISR副本数量 >= `min.in.sync.replicas`（ack = -1时）
* 校验batch的消息大小（包含压缩后的），不超过`max.message.bytes`（默认1M）
* 检查batch的校验和
* 校验batch的消息大小，不超过`max.message.bytes`（segment.bytes，默认1G）
* 是否滚动日志LogSegment
  * LogSegment日志文件满了（不足以写入新的batch消息）
  * LogSegment中第一条消息的创建时间距离当前时间的时间间隔超过`segment.ms`（默认7天）
  * 索引文件写满了，包括`.index`、`.timeindex`
* batch消息追加到LogSegment（LogSegment#append）
  * FileRecords.append()，
  * batch消息对应的ByteBuffer直接写入FileChannel（`.log`日志文件）
  * 按需写入offset索引文件`.index`，每当写入的消息大小和超过`index.interval.bytes`（默认4k）时写入，写入格式: offset -> 消息写入LogSegment的物理地址
  * 按需写入时间索引文件`.timeindex`，满足上面条件且batch消息的时间戳比之前的大，写入格式: 时间戳 -> offset
* 更新nextOffset
* 日志flush，未flush的消息条数超过`flush.messages`（默认Int最大值）时，强制flush日志文件和索引文件，并更新恢复检查点
* 更新HW

## 消费消息

* ReplicaManager#fetchMessages

* 网络模型: https://blog.csdn.net/weixin_45505313/article/details/121874693

## Sender线程

### sendProducerData()

* RecordAccumulator.ready(): 获取有批次ready的节点node列表
  * 更新自适应分区统计
  * 排除节点响应时间超过partitionAvailabilityTimeoutMs
  * 判断批次是否ready
    * max.in.flight.requests.per.connection=1，有进行中请求，
    * 否则判断下面条件
      * 批次队列大小大于1 或 批次队列头节点满了
      * 批次队列头节点非重试，批次等待时间大于linger.ms
      * 批次队列头节点重试，重试等待时间大于retry.backoff.ms
      * 消息缓冲区无可用内存（等待队列不为空）
      * 关闭状态
* 判断节点是否ready
  * 节点可用
  * 节点inFlightRequests数量小于maxInFlightRequestsPerConnection
* RecordAccumulator.drain(): 获取批次
  * 获取节点下的所有的主分区
  * drainIndex++，遍历分区，直到遍历完一轮或批次队列总大小超过maxRequestSize（1M）
  * 每个分区尝试drain头节点批次
    * 重试未满重试时间retryBackoffMs，return
  * 批次关闭，批次加入节点待发送批次列表
* 待发送批次添加到Sender的inFlightBatches
* 严格保证消息顺序性，锁住当前分区，max.in.flight.requests.per.connection=1
  * 全局维度，配置单独的Kafka Producer
* inFlightBatches中超时的批次，deliveryTimeoutMs
* 批次队列中超时的批次，deliveryTimeoutMs
* 超时批次回调，清理
* 按节点node循环发送

### NetworkClient.poll()

AppendCallbacks回调

request.timeout.ms = 30s
InFlightRequests（头部插入，尾部最老的）
max.in.flight.requests.per.connection = 5

### Broker Produce

* Acceptor
* Processor
* KafkaRequestHandler
* minInSyncReplicas
* inSyncSize < minIsr && requiredAcks == -1，返回错误
* 批次大小不超过max.message.bytes，默认1M
* CRC校验
* 压缩类型，CompressionCodec，BrokerCompressionCodec
* logEndOffset

消息校验和分配Offset，分三种情况
* 1、都是未压缩，magic不匹配
* 2、都是未压缩，magic匹配
  * magic大于V2 或 producer端压缩了，一个分区只能有一个批次
  * 校验消息offset和数量是否一致
  * 循环批次的消息进行校验，并递增offset
  * 原地更新BASE_OFFSET_OFFSET
* 3、压缩不匹配
* 再次校验，批次解压后的大小不超过max.message.bytes

加锁

Segment是否需要滚动
* 批次消息的最大时间戳 - Segment第一条消息的时间戳 > maxSegmentMs
* 当前时间 - Segment创建时间 > maxSegmentMs

Segment大小不足（log.segment.bytes=1G）
Segment不为空且时间超过maxSegmentMs（log.roll.ms、log.roll.hours=168h，7天）
offset索引文件满了（log.index.size.max.bytes=10M）
时间戳索引文件满了（log.index.size.max.bytes=10M）
消息的相对偏移量不能超过int最大值

更新批次第一个offset的元信息（messageOffset、segmentBaseOffset、relativePositionInSegment）
append消息批次，更新logEndOffset，
activeSegment

segment大小不超过int最大值（差不多2个G的大小）
写PageCache
segment大小自增
更新索引，写入消息大小超过log.index.interval.bytes（4k）
写offset索引文件，relativeOffset（int），position（int）
写时间戳索引文件，批次的最大时间戳大于上一个索引项的时间戳，则写索引，timestamp(long)，relativeOffset（int）
更新logEndOffset

未flush的大小大于log.flush.interval.messages（默认9223372036854775807）
flush，.log、.index、.timeindex

更新HW等于（各个副本LEO的最小值）

ack == -1
ack等于其它，立刻响应

定时任务：
kafka-log-retention
kafka-log-flusher


ISR & HW & LEO

ISR：ack=-1（minSyncISR）
HW：消费可见性
HW、LEO更新
ISR踢出：LEO相等、超时
ISR加入：副本的LEO大于HW




压缩 & 解压缩
Broker压缩：CPU耗时、失去零拷贝特性、GC问题
批量压缩（耗CPU、节省网络和IO耗时、节省磁盘空间），压缩重复性，压缩比
最佳实现：Producer 端压缩、Broker 端保持、Consumer 端解压缩


https://blog.csdn.net/zl1zl2zl3/article/details/107963699



https://www.jianshu.com/p/e0f23cb5b0e0
https://zhuanlan.zhihu.com/p/120967989

一文读懂弃用默认分区器DefaultPartitioner
https://blog.csdn.net/dreamcatcher1314/article/details/127349140
Kafka消息写入流程
https://blog.csdn.net/u014393917/article/details/128436297



Page Cache
顺序读写（预读机制、写优化）
I/O调度器（合并、排序（按扇区））
零拷贝（https://zhuanlan.zhihu.com/p/183808742）
flush.messages、flush.ms
按TopicPartition循环
找LogSegment，Segment分段（方便删除过期消息，删除策略）
写日志（Socket缓冲区 -> 内存映射文件mmap、Page Cache），刷新机制，批处理
索引文件
顺序写
大量topic/分区数造成性能下降（文件句柄、文件缓存、顺序写退化为随机写），RocketMQ共用一个日志文件

https://www.cnblogs.com/yescode/p/13215869.html
https://blog.csdn.net/qq_30168227/article/details/124484777
https://blog.csdn.net/u012150370/article/details/90111606
https://blog.csdn.net/yhflyl/article/details/123389503
