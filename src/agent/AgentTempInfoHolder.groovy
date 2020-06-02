package agent

import common.LimitQueue
import common.Utils
import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
@Singleton
class AgentTempInfoHolder {
    static enum Type {
        node, container, app
    }

    ConcurrentHashMap<Type, LimitQueue<Map>> info = new ConcurrentHashMap<>()
    ConcurrentHashMap<Integer, LimitQueue<Map>> metric = new ConcurrentHashMap<>()

    private int queueSize = 2880

    AgentTempInfoSender sender

    void add(Type type, Map content) {
        content.time = new Date()
        content.type = type.name()
        content.nodeIp = Agent.instance.nodeIp

        def queue = new LimitQueue<Map>(queueSize)
        queue << content
        def q = info.putIfAbsent(type, queue)
        if (q) {
            synchronized (q) {
                q << content
            }
        }
        if (sender) {
            sender.send(Utils.localIp(), content)
        }
    }

    void addMetric(Integer appId, Integer instanceIndex, Map content, String body) {
        content.time = new Date()
        content.type = Type.app.name()
        content.nodeIp = Agent.instance.nodeIp
        content.appId = appId
        content.instanceIndex = instanceIndex
        content.rawBody = body

        def queue = new LimitQueue<Map>(queueSize)
        queue << content
        def q = metric.putIfAbsent(appId, queue)
        if (q) {
            synchronized (q) {
                q << content
            }
        }
        if (sender) {
            sender.send('' + appId, content)
        }
    }
}
