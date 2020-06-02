package server.scheduler

import com.alibaba.fastjson.JSONObject
import common.ContainerHelper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.AppDTO
import model.AppJobDTO

@CompileStatic
@Slf4j
class ScrollProcessor extends CreateProcessor {
    @Override
    void process(AppJobDTO job, AppDTO app, List<JSONObject> containerList) {
        List<String> nodeIpList = containerList.collect { x ->
            ContainerHelper.getNodeIp(x)
        }
        List<String> targetNodeIpList = app.conf.targetNodeIpList ?: nodeIpList
        if (app.conf.targetNodeTagList || app.conf.excludeNodeTagList) {
            def nodeIpListFilter = chooseNodeList(app.clusterId, app.conf.excludeNodeTagList, null,
                    app.conf.targetNodeTagList).collect { it.ip }
            targetNodeIpList = targetNodeIpList.findAll {
                it in nodeIpListFilter
            }
        }

        if (targetNodeIpList.size() < nodeIpList.size()) {
            int needAddInstanceNum = nodeIpList.size() - targetNodeIpList.size()
            def otherNodeIpList = chooseNodeList(app.clusterId, app.id, needAddInstanceNum, app.conf, targetNodeIpList)
            targetNodeIpList += otherNodeIpList
        }
        log.info 'choose node - ' + targetNodeIpList + ' before - ' + nodeIpList

        nodeIpList.eachWithIndex { String nodeIp, int i ->
            def x = containerList[i]
            def instanceIndex = ContainerHelper.getAppInstanceIndex(x)
            if (instanceIndex != i) {
                log.warn 'instance index not match - ' + i + ' - ' + instanceIndex
            }

            def keeper = stopOneContainer(job.id, app, x)
            // start new container
            if (targetNodeIpList.size() <= i) {
                return
            }

            def confCopy = app.conf.copy()
            def abConf = app.abConf
            if (abConf) {
                if (instanceIndex < abConf.containerNumber) {
                    confCopy.image = abConf.image
                }
            }

            def newNodeIp = targetNodeIpList[i]
            def result = startOneContainer(app.clusterId, app.id, job.id, instanceIndex,
                    targetNodeIpList, newNodeIp, confCopy, keeper)
            addToGateway(result, instanceIndex, app)
        }


    }
}
