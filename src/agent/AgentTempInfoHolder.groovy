package agent

import common.LimitQueue
import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
@Singleton
class AgentTempInfoHolder {
    static enum Type {
        node, container, app
    }

    LimitQueue<Map> nodeQueue
    ConcurrentHashMap<Integer, LimitQueue<Map>> appMetricQueues = new ConcurrentHashMap<>()
    ConcurrentHashMap<Integer, Set<String>> appMetricGaugeNameSet = new ConcurrentHashMap<>()
    ConcurrentHashMap<Integer, LimitQueue<Map>> containerMetricQueues = new ConcurrentHashMap<>()

    private int queueSize = 2880

    AgentTempInfoSender sender

    void addNode(Map content) {
        content.time = new Date()
        content.type = Type.node.name()
        content.nodeIp = Agent.instance.nodeIp

        if (nodeQueue == null) {
            nodeQueue = new LimitQueue<Map>(queueSize)
        }
        nodeQueue << content
        if (sender) {
            sender.send(Agent.instance.nodeIp, content)
        }
    }

    void addAppMetric(Integer appId, Integer instanceIndex, Map content, String body) {
        def set = new HashSet(content.keySet())
        set.remove('containerId')
        appMetricGaugeNameSet[appId] = set

        content.time = new Date()
        content.type = Type.app.name()
        content.nodeIp = Agent.instance.nodeIp
        content.appId = appId
        content.instanceIndex = instanceIndex
        content.rawBody = body

        def queue = new LimitQueue<Map>(queueSize)
        queue << content
        def q = appMetricQueues.putIfAbsent(appId, queue)
        if (q) {
            synchronized (q) {
                q << content
            }
        }
        if (sender) {
            sender.send('' + appId, content)
        }
    }

    void addContainerMetric(String containerId, Integer appId, Integer instanceIndex, Map content) {
        content.time = new Date()
        content.type = Type.container.name()
        content.nodeIp = Agent.instance.nodeIp
        content.containerId = containerId
        content.appId = appId
        content.instanceIndex = instanceIndex

        def queue = new LimitQueue<Map>(queueSize)
        queue << content
        def q = containerMetricQueues.putIfAbsent(appId, queue)
        if (q) {
            synchronized (q) {
                q << content
            }
        }
        if (sender) {
            sender.send('' + appId, content)
        }
    }

    void clear(Integer appId) {
        appMetricQueues.remove(appId)
        appMetricGaugeNameSet.remove(appId)
        containerMetricQueues.remove(appId)
    }
}
