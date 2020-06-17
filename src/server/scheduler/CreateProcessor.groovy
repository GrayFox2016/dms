package server.scheduler

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import common.Conf
import ex.JobProcessException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.*
import model.json.AppConf
import model.json.ContainerResourceAsk
import org.apache.commons.lang.exception.ExceptionUtils
import org.segment.d.json.JsonWriter
import org.segment.web.common.CachedGroovyClassLoader
import server.AgentCaller
import server.InMemoryAllContainerManager
import server.dns.DnsOperator
import server.dns.EtcdClientHolder
import server.gateway.GatewayOperator

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

import static common.ContainerHelper.*

@CompileStatic
@Slf4j
class CreateProcessor implements GuardianProcessor {
    @Override
    void process(AppJobDTO job, AppDTO app, List<JSONObject> containerList) {
        def conf = app.conf
        int containerNumber = conf.containerNumber
        List<Integer> needRunInstanceIndexList = job.param('needRunInstanceIndexList') as List<Integer>
        List<Integer> instanceIndexList = []
        if (needRunInstanceIndexList != null) {
            instanceIndexList.addAll(needRunInstanceIndexList)
        } else {
            (0..<containerNumber).each { Integer i ->
                instanceIndexList << i
            }
        }

        def runNumber = instanceIndexList.size()
        List<String> nodeIpList = []
        def targetNodeIpList = conf.targetNodeIpList
        if (targetNodeIpList) {
            if (targetNodeIpList.size() < runNumber) {
                throw new JobProcessException('node not ok - ' + runNumber + ' but available node number - ' + targetNodeIpList.size())
            }
            instanceIndexList.each { i ->
                nodeIpList << targetNodeIpList[i]
            }
        } else {
            nodeIpList.addAll(chooseNodeList(app.clusterId, app.id, runNumber, conf))
        }
        log.info 'choose node - ' + nodeIpList + ' for ' + app.id + ' - ' + instanceIndexList
        def nodeIpListCopy = targetNodeIpList ?: nodeIpList

        if (!conf.isParallel) {
            nodeIpList.eachWithIndex { String nodeIp, int i ->
                def instanceIndex = instanceIndexList[i]
                def confCopy = conf.copy()
                def abConf = app.abConf
                if (abConf) {
                    if (instanceIndex < abConf.containerNumber) {
                        confCopy.image = abConf.image
                        confCopy.tag = abConf.tag
                    }
                }

                def result = startOneContainer(app.clusterId, app.id, job.id, instanceIndex, nodeIpListCopy, nodeIp, confCopy)
                addToGateway(result, instanceIndex, app)
            }
            return
        }

        ConcurrentHashMap<Integer, Exception> exceptionByInstanceIndex = new ConcurrentHashMap<>()
        def latch = new CountDownLatch(nodeIpList.size())
        nodeIpList.eachWithIndex { String nodeIp, int i ->
            Integer instanceIndex = instanceIndexList[i]
            AppConf confCopy = conf.copy()
            def abConf = app.abConf
            if (abConf) {
                if (instanceIndex < abConf.containerNumber) {
                    confCopy.image = abConf.image
                    confCopy.tag = abConf.tag
                }
            }

            Thread.start {
                try {
                    def result = startOneContainer(app.clusterId, app.id, job.id, instanceIndex, nodeIpListCopy, nodeIp, confCopy)
                    addToGateway(result, instanceIndex, app)
                } catch (Exception e) {
                    log.error('start one container error - ' + confCopy.image + ' - ' + instanceIndex + ' - ' + nodeIp, e)
                    exceptionByInstanceIndex[instanceIndex] = e
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()

        if (exceptionByInstanceIndex) {
            throw new JobProcessException(exceptionByInstanceIndex.collect {
                "${it.key} - " + ExceptionUtils.getFullStackTrace(it.value)
            }.join("\r\n"))
        }
    }

    protected void addToGateway(ContainerRunResult result, int instanceIndex, AppDTO app) {
        result.nodeIp = InMemoryAllContainerManager.instance.getNodeIpDockerHost(result.nodeIp)
        def gatewayConf = app.gatewayConf
        if (gatewayConf) {
            def abConf = app.abConf
            int w = abConf && instanceIndex < abConf.containerNumber ? abConf.weight :
                    GatewayOperator.DEFAULT_WEIGHT
            result.extract(gatewayConf)

            def isAddOk = GatewayOperator.create(app.id, gatewayConf).addBackend(result, w)
            String message = "add to cluster ${gatewayConf.clusterId} frontend ${gatewayConf.frontendId} - ${isAddOk}"
                    .toString()
            result.keeper.next(JobStepKeeper.Step.addToGateway, message)
            result.keeper.next(JobStepKeeper.Step.done, '', isAddOk)

//            if (!isAddOk) {
//                throw new JobProcessException('add to gateway fail - ' + result.nodeIp + ':' + result.port)
//            }
        } else {
            result.keeper.next(JobStepKeeper.Step.done)
        }
    }

    protected List<NodeDTO> chooseNodeList(int clusterId, List<String> excludeNodeTagList,
                                           List<String> excludeNodeIpList, List<String> targetNodeTagList) {
        def nodeList = InMemoryAllContainerManager.instance.getHeartBeatOkNodeList(clusterId)
        if (!nodeList) {
            throw new JobProcessException('node not ready')
        }

        List<NodeDTO> list = nodeList
        if (excludeNodeTagList) {
            list = list.findAll {
                if (!it.tags) {
                    return true
                }
                !it.tags.split(',').any { tag -> tag in excludeNodeTagList }
            }
        }
        if (targetNodeTagList) {
            list = list.findAll {
                if (!it.tags) {
                    return false
                }
                it.tags.split(',').any { tag -> tag in targetNodeTagList }
            }
        }
        if (excludeNodeIpList) {
            list = list.findAll {
                !(it.ip in excludeNodeIpList)
            }
        }
        list
    }

    List<String> chooseNodeList(int clusterId, int appId, int runNumber, AppConf conf, List<String> excludeNodeIpList = null) {
        def nodeList = chooseNodeList(clusterId, conf.excludeNodeTagList, excludeNodeIpList, conf.targetNodeTagList)

        def containerList = InMemoryAllContainerManager.instance.getContainerList(clusterId)
        Map<String, List<JSONObject>> groupByNodeIp = containerList.findAll { x ->
            getAppName(x) != APP_NAME_OTHER
        }.groupBy { x ->
            getNodeIp(x)
        }

        boolean isLimitNode = conf.envList.any { it.key?.contains('X_LIMIT_NODE') }
        // exclude node that has this app's running container
        def list = nodeList.findAll {
            def subList = groupByNodeIp[it.ip]
            if (!subList) {
                return true
            }
            if (isLimitNode) {
                return true
            }

            def isThisNodeIncludeThisAppContainer = subList.any { x ->
                appId == getAppId(x) && isRunning(x)
            }
            !isThisNodeIncludeThisAppContainer
        }
        if (list.size() < runNumber) {
            if (isLimitNode) {
                (runNumber - list.size()).times {
                    list << list[-1]
                }
            } else {
                throw new JobProcessException('node not enough - ' + runNumber + ' but only have node available - ' + list.size())
            }
        }

        Map<Integer, AppDTO> otherAppCached = [:]
        List<ContainerResourceAsk> leftResourceList = []

        def allNodeInfo = InMemoryAllContainerManager.instance.allNodeInfo
        for (node in list) {
            def nodeInfo = allNodeInfo[node.ip]
            def cpuPercList = nodeInfo.getJSONArray('cpuPercList')
            def mem = nodeInfo.getJSONObject('mem')
            int memMBTotal = mem.getLong('total').intValue()

            def subList = groupByNodeIp[node.ip]
            List<ContainerResourceAsk> otherAppResourceAskList = subList ? subList.collect { x ->
                def otherAppId = getAppId(x)
                def otherApp = otherAppCached[otherAppId]
                if (!otherApp) {
                    def appOne = new AppDTO(id: otherAppId).queryFields('conf').one() as AppDTO
                    otherAppCached[otherAppId] = appOne
                    return new ContainerResourceAsk(appOne.conf)
                } else {
                    return new ContainerResourceAsk(otherApp.conf)
                }
            } : [] as List<ContainerResourceAsk>

            int memMBUsed = 0
            int cpuShareUsed = 0
            double cpuFixedUsed = 0
            for (one in otherAppResourceAskList) {
                memMBUsed += one.memMB
                cpuShareUsed += one.cpuShare
                cpuFixedUsed += one.cpuFixed
            }

            def leftResource = new ContainerResourceAsk(nodeIp: node.ip,
                    memMB: memMBTotal - memMBUsed,
                    cpuShare: cpuPercList.size() * 1024 - cpuShareUsed,
                    cpuFixed: cpuPercList.size() - cpuFixedUsed)
            if (leftResource.memMB < conf.memMB) {
                log.warn 'mem left no enough for ' + conf.memMB + ' but left ' + leftResource.memMB + ' on ' + node.ip
            } else {
                leftResourceList << leftResource
            }
        }
        if (leftResourceList.size() < runNumber) {
            throw new JobProcessException('node not enough - ' + runNumber + ' but only have node available - ' + leftResourceList.size())
        }

        List<String> nodeIpList = []
        // sort by agent script
        def sortR = AgentCaller.instance.agentScriptExe(list[0].ip, 'node choose',
                [leftResourceListJson: JSON.toJSONString(leftResourceList)])
        def arrR = sortR.getJSONArray('list')
        arrR[0..<runNumber].each {
            nodeIpList << it.toString()
        }
        nodeIpList
    }

    JobStepKeeper stopOneContainer(int jobId, AppDTO app, JSONObject x) {
        def nodeIp = getNodeIp(x)
        def instanceIndex = getAppInstanceIndex(x)
        def keeper = new JobStepKeeper(jobId: jobId, instanceIndex: instanceIndex, nodeIp: nodeIp)
        def gatewayConf = app.gatewayConf
        if (gatewayConf) {
            def publicPort = getPublicPort(gatewayConf.containerPrivatePort, x)
            if (!publicPort) {
                throw new JobProcessException('no public port get for ' + app.name)
            }

            def nodeIpDockerHost = InMemoryAllContainerManager.instance.getNodeIpDockerHost(nodeIp)
            def isRemoveBackendOk = GatewayOperator.create(app.id, gatewayConf).removeBackend(nodeIpDockerHost, publicPort)
            keeper.next(JobStepKeeper.Step.removeFromGateway, 'remove backend result - ' + isRemoveBackendOk)
        }

        def id = getContainerId(x)
        def p = [id: id]

        try {
            def r = AgentCaller.instance.agentScriptExe(nodeIp, 'container inspect', p)
            def containerInspectInfo = r.getJSONObject('container')
            def state = containerInspectInfo.getJSONObject('State').getString('Status')
            if ('created' == state || 'running' == state) {
                p.isRemoveAfterStop = '1'
                AgentCaller.instance.agentScriptExe(nodeIp, 'container stop', p)
                log.info 'done stop and remove container - ' + id + ' - ' + app.id
            } else if ('exited' == state) {
                AgentCaller.instance.agentScriptExe(nodeIp, 'container remove', p)
                log.info 'done remove container - ' + id + ' - ' + app.id
            }
            keeper.next(JobStepKeeper.Step.stopAndRemoveContainer, 'state: ' + state)
            keeper.next(JobStepKeeper.Step.done)
        } catch (Exception e) {
            log.error('get container info error - ' + id + ' for app - ' + app.name, e)
            keeper.next(JobStepKeeper.Step.stopAndRemoveContainer, 'error: ' + e.message, false)
            keeper.next(JobStepKeeper.Step.done, '', false)
        }
        keeper
    }

    ContainerRunResult startOneContainer(int clusterId, int appId, int jobId, int instanceIndex,
                                         List<String> nodeIpList, String nodeIp, AppConf conf, JobStepKeeper passedKeeper = null) {
        def registryOne = new ImageRegistryDTO(id: conf.registryId).one() as ImageRegistryDTO
        assert registryOne
        def imageWithTag = registryOne.trimScheme() + '/' + conf.group + '/' + conf.image + ':' + conf.tag

        def keeper = passedKeeper ?: new JobStepKeeper(jobId: jobId, instanceIndex: instanceIndex, nodeIp: nodeIp)
        keeper.next(JobStepKeeper.Step.chooseNode, nodeIpList.toString())

        def p = [keyword: imageWithTag]
        def listImageR = AgentCaller.instance.agentScriptExe(nodeIp, 'container image list', p)
        def imageList = listImageR.getJSONArray('list')
        if (!imageList) {
            // need pull
            p.image = imageWithTag
            p.registryId = conf.registryId
            p.readTimeout = 1000 * 32
            def pullImageR = AgentCaller.instance.agentScriptExe(nodeIp, 'container image pull', p)
            Boolean isError = pullImageR.getBoolean('isError')
            if (isError && isError.booleanValue()) {
                throw new JobProcessException('pull image fail - ' + imageWithTag + ' - ' + pullImageR.getString('message'))
            }

            def listImageAgainR = AgentCaller.instance.agentScriptExe(nodeIp, 'container image list', p)
            def imageListAgain = listImageAgainR.getJSONArray('list')
            if (imageListAgain) {
                keeper.next(JobStepKeeper.Step.pullImage, 'done pulled image ' + imageWithTag)
            } else {
                throw new JobProcessException('pull image fail - ' + imageWithTag + ' - ' + nodeIp)
            }
        } else {
            keeper.next(JobStepKeeper.Step.pullImage, 'skip pulled image ' + imageWithTag)
        }

        def createContainerConf = new CreateContainerConf()
        createContainerConf.conf = conf
        createContainerConf.clusterId = clusterId
        createContainerConf.appId = appId
        createContainerConf.nodeIp = nodeIp
        createContainerConf.nodeIpDockerHost = InMemoryAllContainerManager.instance.getNodeIpDockerHost(nodeIp)
        createContainerConf.instanceIndex = instanceIndex
        createContainerConf.nodeIpList = nodeIpList
        createContainerConf.nodeIpDockerHostList = nodeIpList.collect {
            InMemoryAllContainerManager.instance.getNodeIpDockerHost(it)
        }
        createContainerConf.imageWithTag = imageWithTag

        def cluster = new ClusterDTO(id: clusterId).one() as ClusterDTO
        createContainerConf.globalEnvConf = cluster.globalEnvConf
        def appList = new AppDTO(clusterId: clusterId).loadList() as List<AppDTO>
        createContainerConf.appIdList = appList.collect { it.id }
        log.info createContainerConf.toString()

        Map<String, Object> evalP = [:]
        evalP.createContainerConf = createContainerConf

        def tplList = new ImageTplDTO(imageName: conf.group + '/' + conf.image).loadList() as List<ImageTplDTO>
        def preList = tplList.findAll { it.tplType == ImageTplDTO.TplType.checkPre.name() }
        def initList = tplList.findAll { it.tplType == ImageTplDTO.TplType.init.name() }
        def afterList = tplList.findAll { it.tplType == ImageTplDTO.TplType.checkAfter.name() }

        if (preList && !conf.isParallel) {
            for (pre in preList) {
                def isCheckOk = CachedGroovyClassLoader.instance.eval(pre.content, evalP) as boolean
                if (!isCheckOk) {
                    throw new JobProcessException('container run pre check fail - ' + instanceIndex + ' - when check ' + pre.name)
                }
                keeper.next(JobStepKeeper.Step.preCheck, pre.name)
            }
        } else {
            keeper.next(JobStepKeeper.Step.preCheck, 'skip')
        }

        // node volume conflict check
        def skipVolumeDirSet = cluster.globalEnvConf.skipConflictCheckVolumeDirList.collect { it.value.toString() }
        def otherAppMountVolumeDirList = appList.findAll { it.id != appId }.collect { app ->
            app.conf.dirVolumeList.collect { it.dir }.findAll { !(it in skipVolumeDirSet) }
        }.flatten()
        def thisAppMountVolumeDirList = conf.dirVolumeList.collect { it.dir }.findAll { !(it in skipVolumeDirSet) }
        if (thisAppMountVolumeDirList.any { it in otherAppMountVolumeDirList }) {
            throw new JobProcessException('node volume conflict check fail - ' + nodeIp + ' - ' + thisAppMountVolumeDirList)
        }

        def createP = [jsonStr: JsonWriter.instance.json(createContainerConf)]
        def createR = AgentCaller.instance.agentScriptExe(nodeIp, 'container create', createP)
        Boolean isError = createR.getBoolean('isError')
        if (isError && isError.booleanValue()) {
            throw new JobProcessException('create container fail - ' + imageWithTag + ' - ' + createR.getString('message'))
        }

        def containerConfig = createR.getJSONObject('containerConfig')
        def containerId = createR.getString('containerId')
        keeper.next(JobStepKeeper.Step.createContainer, 'id: ' + containerId + ' config: ' + containerConfig.toString())

        def startR = AgentCaller.instance.agentScriptExe(nodeIp, 'container start', [id: containerId])
        Boolean isErrorStart = startR.getBoolean('isError')
        if (isErrorStart && isErrorStart.booleanValue()) {
            throw new JobProcessException('start container fail - ' + imageWithTag + ' - ' + startR.getString('message'))
        }
        keeper.next(JobStepKeeper.Step.startContainer, 'id: ' + containerId)

        // update dns
        if (cluster.globalEnvConf.dnsEndpoints && cluster.globalEnvConf.dnsKeyPrefix) {
            def dnsTtl = Conf.instance.getInt('dnsTtl', 3600)
            def client = EtcdClientHolder.instance.create(cluster.globalEnvConf.dnsEndpoints)
            boolean isOk = new DnsOperator(client, cluster.globalEnvConf.dnsKeyPrefix).
                    put(generateContainerHostname(appId, instanceIndex), nodeIp, dnsTtl)
            keeper.next(JobStepKeeper.Step.updateDns, 'done update dns record - ' + isOk)
        }

        if (initList) {
            for (init in initList) {
                String initCmd = CachedGroovyClassLoader.instance.eval(init.content, evalP).toString()
                if (initCmd) {
                    def initR = AgentCaller.instance.agentScriptExe(nodeIp, 'container init',
                            [id: containerId, initCmd: initCmd])
                    Boolean isErrorInit = initR.getBoolean('isError')
                    if (isErrorInit && isErrorInit.booleanValue()) {
                        throw new JobProcessException('init container fail - ' + conf.group + '/' + conf.image + ' - ' + initR.getString('message'))
                    }
                    keeper.next(JobStepKeeper.Step.initContainer, 'done ' + init.name + ' - ' + initCmd + ' - ' +
                            initR.getString('message'))
                } else {
                    keeper.next(JobStepKeeper.Step.initContainer, 'skip ' + init.name)
                }
            }
        }

        if (afterList && !conf.isParallel) {
            for (after in afterList) {
                def isCheckOk = CachedGroovyClassLoader.instance.eval(after.content, evalP) as boolean
                if (!isCheckOk) {
                    throw new JobProcessException('container run after check fail - ' + instanceIndex + ' - when check ' + after.name)
                }
                keeper.next(JobStepKeeper.Step.afterCheck, after.name)
            }
        } else {
            keeper.next(JobStepKeeper.Step.afterCheck, 'skip')
        }

        new ContainerRunResult(nodeIp: nodeIp, containerConfig: containerConfig, keeper: keeper)
    }
}
