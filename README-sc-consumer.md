# Kafka Consumer

## Consumer启动

* 默认config和用户自定义的Properties合并
* 拦截器列表实例化
* key/value序列化器实例化
* auto.offset.reset，默认latest
* SubscriptionState
* ConsumerMetadata
* 解析Kafka集群地址
* ConsumerNetworkClient、NetworkClient、Selector
* partition.assignment.strategy，ConsumerPartitionAssignor
* RangeAssignor、CooperativeStickyAssignor
* ConsumerCoordinator，消费者协调器
  * Heartbeat
* RebalanceProtocol
* Fetcher
  * OffsetsForLeaderEpochClient

## subscribe()

* ConsumerRebalanceListener
* SubscriptionType
* 订阅的topic列表
* requestUpdateForNewTopics，设置标识

## poll()

* 判断订阅类型是否存在
* ConsumerCoordinator.poll()
* 按需更新元数据
  * offset提交完成队列的节点poll()，然后invoke()
  * 如果是AUTO_TOPICS 或 AUTO_PATTERN
  * Coordinator不存在，查找Coordinator
    * 找一个节点询问coordinator
    * 发送FindCoordinator请求（传递group_id），sendFindCoordinatorRequest()
    * 请求添加到UnsentRequests
    * 唤醒NetworkClient
    * FindCoordinator请求响应回调
      * 判断Coordinator只能有一个
      * Coordinator的id = int最大值 - Coordinator节点的nodeId
      * 记录Coordinator的id、host、port
      * 尝试连接Coordinator节点
    * 返回future
  * ConsumerNetworkClient.poll()，值到时间超时或FindCoordinator请求的future完成
    * 处理pendingCompletion队列，回调
    * 处理pendingDisconnects队列，unsent的请求失败，连接断开
    * 发送unsent的请求
    * NetworkClient.poll()
      * IO读写事件
    * 开启HeartbeatThread
    * joinFuture
    * sendJoinGroupRequest()，发送JoinGroupRequest请求给Coordinator，JoinGroupResponseHandler
    * 只有Leader节点可分配，解析分区列表
    * assignor.onAssignment
    * listener.onPartitionsAssigned

## Consumer请求

组协调器（分布到各个Broker） 消费者协调器（消费者）

* MetadataRequest MetadataResponse（集群元数据）

* FindCoordinatorRequest FindCoordinatorResponse（组协调器）FindCoordinatorResponseHandler
  * 选择一个节点: 负载最小的节点，进行中的请求数为0或最小的节点
  * topic: __consumer_offsets
  * partition = hash(groupId) % offsets.topic.num.partitions，50
  * partition -> leader节点
  * 返回: (groupId, nodeId, host, port)

后续分组请求发送给组协调器节点、尝试连接

* HeartbeatRequest HeartbeatResponse（开启心跳线程）

* JoinGroupRequest JoinGroupResponse（加入消费组，两次）JoinGroupResponseHandler
  * 没有memberId: 生成memberId，加入pendingMembers，返回response: MEMBER_ID_REQUIRED和memberId
    * memberId生成规则: groupId-num-uuid
  * 有memberId: 检查是否在pendingMembers中，（再次加入，memberId有时间限制）
    * 返回: 开启心跳
    * 成功加入消费组
      * 当前消费者是Leader
        * 消费组分配
        * 发送SyncGroupRequest
      * 当前消费者是Follower
        * SyncGroupRequest
  * Leader: 第一个加入的为Leader，
    * 选择分区分配策略

* SyncGroupRequest SyncGroupResponse SyncGroupResponseHandler
  * ConsumerCoordinator#onLeaderElected

* OffsetFetchRequest OffsetFetchResponse OffsetFetchResponseHandler
  * KafkaConsumer#committed
  * 回调: updateLastSeenEpochIfNewer
  * 更新分区Leader的epoch

* ListOffsetsRequest ListOffsetsResponse
  * Fetcher#sendListOffsetRequest
  * Fetcher#resetOffsetsAsync
  * 更新Fetch position  

* FetchRequest FetchResponse

https://blog.csdn.net/weixin_40482816/article/details/127513743
