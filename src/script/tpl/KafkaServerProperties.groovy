package script.tpl

import server.scheduler.ContainerMountTplHelper

def instanceIndex = super.binding.getProperty('instanceIndex')
def nodeIpDockerHost = super.binding.getProperty('nodeIpDockerHost')
def logDirs = super.binding.getProperty('logDirs')
def numPartitions = super.binding.getProperty('numPartitions')
def logRetentionHours = super.binding.getProperty('logRetentionHours')

def zkAppName = super.binding.getProperty('zkAppName')

ContainerMountTplHelper applications = super.binding.getProperty('applications')
def zkApp = applications.app(zkAppName)
//def zookeeperConnect = zkApp.getAllNodeIpList().collect { it + ':2181' }.join(',')
def firstNodeIp = zkApp.getAllNodeIpList()[0]
def zookeeperConnect = [2181, 2182, 2183].collect { firstNodeIp + ':' + it }.join(',')

"""
broker.id=${instanceIndex}

listeners=PLAINTEXT://0.0.0.0:${9092 + instanceIndex}
advertised.listeners=PLAINTEXT://${nodeIpDockerHost}:${9092 + instanceIndex}

num.network.threads=3
num.io.threads=8

socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

log.dirs=${logDirs}

num.partitions=${numPartitions}

num.recovery.threads.per.data.dir=1

offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1

log.retention.hours=${logRetentionHours}
log.segment.bytes=1073741824

log.retention.check.interval.ms=300000

zookeeper.connect=${zookeeperConnect}
zookeeper.connection.timeout.ms=18000

group.initial.rebalance.delay.ms=0
"""