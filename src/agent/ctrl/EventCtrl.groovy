package agent.ctrl

import agent.Agent
import agent.AgentTempInfoHolder
import agent.AgentTempInfoHolder.Type
import org.segment.d.D
import org.segment.d.Pager
import org.segment.web.handler.ChainHandler

def h = ChainHandler.instance

h.group('/event') {
    h.get('/list') { req, resp ->
        def p = req.param('pageNum')
        int pageNum = p ? p as int : 1
        final int pageSize = 10

        def type = req.param('type')
        def reason = req.param('reason')
        def appId = req.param('appId')

        def list = Agent.instance.eventQueue.findAll {
            if (type && it.type.name() != type) {
                return false
            }
            if (reason && it.reason != reason) {
                return false
            }
            if (appId) {
                if (it.type.name() != 'app' || it.result != appId.toString()) {
                    return false
                }
            }
            true
        }
        def pager = new Pager(pageNum, pageSize)
        pager.totalCount = list.size()
        pager.list = list[pager.start..<pager.end]
        pager
    }.get('/reason/list') { req, resp ->
        Set<String> set = []
        Agent.instance.eventQueue.each {
            set << it.reason
        }
        set.collect { [reason: it] }
    }
}

h.get('/metric/queue') { req, resp ->
    def queueType = req.param('queueType') ?: Type.node.name()
    def type = req.param('type')
    def appId = req.param('appId')
    def instanceIndex = req.param('instanceIndex')
    def containerId = req.param('containerId')

    def list = queueType == Type.node.name() ? AgentTempInfoHolder.instance.nodeQueue :
            (queueType == Type.app.name() ? AgentTempInfoHolder.instance.appMetricQueues[appId as int] :
                    AgentTempInfoHolder.instance.containerMetricQueues[appId as int])
    if (!list) {
        return [:]
    }

    List<Map> ll
    if (queueType == Type.app.name() || queueType == Type.container.name()) {
        if (containerId) {
            ll = list.findAll { map ->
                map.containerId == containerId
            }
        } else if (instanceIndex) {
            ll = list.findAll { map ->
                map.instanceIndex == (instanceIndex as int)
            }
        }
    }

    [timelineList: ll.collect {
        Date time = it.time
        time.format(D.ymdhms)
    }, list      : ll.collect {
        if (queueType == Type.node.name()) {
            if ('cpu' == type) {
                List<Map> cpuPercList = it.cpuPercList
                def percentList = cpuPercList.collect { Map x -> (x.sys as double) + (x.user as long) }
                def r = ((percentList.sum() as double) / (percentList.size())) as double
                return (r * 100).round(4)
            } else if ('mem' == type) {
                Map mem = it.mem
                return mem.usedPercent
            }
        } else if (queueType == Type.container.name()) {
            if ('cpu' == type) {
                return it.cpuPerc
            } else if ('mem' == type) {
                return it.memUsage
            }
        }
    }]
}
