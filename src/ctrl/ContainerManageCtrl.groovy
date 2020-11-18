package ctrl

import auth.User
import com.alibaba.fastjson.JSONObject
import common.ContainerHelper
import model.AppDTO
import model.ImageTplDTO
import org.segment.web.common.CachedGroovyClassLoader
import org.segment.web.handler.ChainHandler
import org.segment.web.handler.Req
import org.segment.web.handler.Resp
import server.AgentCaller
import server.InMemoryAllContainerManager
import server.gateway.GatewayOperator
import server.scheduler.ContainerMountFileGenerator
import server.scheduler.ContainerRunResult
import spi.SpiSupport

import java.math.RoundingMode

def h = ChainHandler.instance

h.group('/container/manage') {
    h.get('/start') { req, resp ->
        callAgentScript(req, resp, 'container start')
    }.get('/stop') { req, resp ->
        callAgentScript(req, resp, 'container stop')
    }.get('/remove') { req, resp ->
        callAgentScript(req, resp, 'container remove')
    }.get('/kill') { req, resp ->
        callAgentScript(req, resp, 'container kill')
    }.get('/inspect') { req, resp ->
        callAgentScript(req, resp, 'container inspect')
    }.get('/list') { req, resp ->
        def clusterId = req.param('clusterId')
        assert clusterId
        def appId = req.param('appId')
        def nodeIp = req.param('nodeIp')
        User user = req.session('user')
        def containerList = InMemoryAllContainerManager.instance.getContainerList(clusterId as int,
                appId ? appId as int : 0, nodeIp, user) ?: []
        def simpleContainerList = containerList.findAll { x ->
            ContainerHelper.getAppName(x) != ContainerHelper.APP_NAME_OTHER
        }.collect { x ->
            def r = new JSONObject(x)
            r.remove('Mounts')
            r.remove('Names')
            r.remove('NetworkSettings')
            r.remove('Labels')
            r
        }

        def appList = new AppDTO().where('cluster_id=?', clusterId as int).
                queryFields('id,name,des,namespace_id,conf').loadList() as List<AppDTO>
        for (x in simpleContainerList) {
            def appOne = appList.find { it.id == ContainerHelper.getAppId(x) }
            if (appOne) {
                x.appName = appOne.name
                x.appDes = appOne.des
                x.namespaceId = appOne.namespaceId
            }
        }

        def groupByApp = simpleContainerList.groupBy { x ->
            ContainerHelper.getAppId(x)
        }
        def groupByNodeIp = simpleContainerList.groupBy { x ->
            ContainerHelper.getNodeIp(x)
        }
        List<Map> appCheckOkList = []
        groupByApp.each { k, v ->
            if (k == 0) {
                appCheckOkList << [appId: k, isOk: true]
            } else {
                def appOne = appList.find { it.id == k }
                if (!appOne) {
                    appCheckOkList << [appId: k, isOk: true]
                } else {
                    boolean isOk = v.count { x -> ContainerHelper.isRunning(x) } == appOne.conf.containerNumber
                    appCheckOkList << [appId: k, isOk: isOk]
                }
            }
        }

        [groupByApp: groupByApp, groupByNodeIp: groupByNodeIp, appCheckOkList: appCheckOkList]
    }.get('/bind/list') { req, resp ->
        def id = req.param('id')
        assert id
        def nodeIp = InMemoryAllContainerManager.instance.getNodeIpByContainerId(id)
        if (!nodeIp) {
            resp.halt(500, 'no node ip get')
        }

        def appId = InMemoryAllContainerManager.instance.getAppIpByContainerId(id)
        if (!appId) {
            resp.halt(500, 'no app ip get')
        }

        User user = req.session('user')
        if (!user.isAccessApp(appId)) {
            resp.halt(403, 'not this app manager')
        }

        def r = AgentCaller.instance.agentScriptExe(nodeIp, 'container inspect', [id: id])
        def container = r.getJSONObject('container')
        def hostConfig = container.getJSONObject('HostConfig')
        def binds = hostConfig.getJSONArray('Binds')

        def hostDirs = binds.collect { it.split(':')[0] }.findAll { !it.toString().endsWith('.file') }.join(',')
        if (hostDirs) {
            def r2 = AgentCaller.instance.agentScriptExe(nodeIp, 'file system dir usage', [dirs: hostDirs])
            binds.collect {
                def arr = it.split(':')
                def hostDir = arr[0]
                Map item = [hostDir: hostDir, containerDir: arr[1], mode: arr[2], fileType: 'file']

                JSONObject dirUsage = r2.getJSONObject(hostDir)
                if (dirUsage) {
                    long diskUsage = dirUsage.getLong('diskUsage')
                    double diskUsageMB = (diskUsage / 1024 / 1024).setScale(2, RoundingMode.FLOOR)
                    dirUsage.diskUsageMB = diskUsageMB
                    item.fileType = 'dir'
                    item.dirUsage = dirUsage
                }
                item
            }
        } else {
            binds.collect {
                def arr = it.split(':')
                [hostDir: arr[0], containerDir: arr[1], mode: arr[2], fileType: 'file']
            }
        }
    }.get('/bind/content') { req, resp ->
        def id = req.param('id')
        assert id
        def nodeIp = InMemoryAllContainerManager.instance.getNodeIpByContainerId(id)
        if (!nodeIp) {
            resp.halt(500, 'no node ip get')
        }

        def appId = InMemoryAllContainerManager.instance.getAppIpByContainerId(id)
        if (!appId) {
            resp.halt(500, 'no app ip get')
        }

        User user = req.session('user')
        if (!user.isAccessApp(appId)) {
            resp.halt(403, 'not this app manager')
        }

        def r = AgentCaller.instance.agentScriptExe(nodeIp, 'container inspect', [id: id])
        def container = r.getJSONObject('container')
        def hostConfig = container.getJSONObject('HostConfig')
        def binds = hostConfig.getJSONArray('Binds')

        def bindFileIndex = req.param('bindFileIndex') as int
        if (bindFileIndex >= binds.size()) {
            resp.end 'no content'
            return
        }

        def arr = binds[bindFileIndex].toString().split(':')
        def r2 = AgentCaller.instance.agentScriptExe(nodeIp, 'file content', [path: arr[0]])
        resp.end r2.content ?: ''
    }.get('/port/bind') { req, resp ->
        def id = req.param('id')
        assert id
        def nodeIp = InMemoryAllContainerManager.instance.getNodeIpByContainerId(id)
        if (!nodeIp) {
            resp.halt(500, 'no node ip get')
        }

        def appId = InMemoryAllContainerManager.instance.getAppIpByContainerId(id)
        if (!appId) {
            resp.halt(500, 'no app ip get')
        }

        User user = req.session('user')
        if (!user.isAccessApp(appId)) {
            resp.halt(403, 'not this app manager')
        }

        def r = AgentCaller.instance.agentScriptExe(nodeIp, 'container inspect', [id: id])
        def container = r.getJSONObject('container')
        def hostConfig = container.getJSONObject('HostConfig')
        hostConfig.getJSONObject('PortBindings') ?: 'No Port Bindings'
    }
}

private void callAgentScript(Req req, Resp resp, String scriptName) {
    def id = req.param('id')
    assert id
    def nodeIp = InMemoryAllContainerManager.instance.getNodeIpByContainerId(id)
    if (!nodeIp) {
        resp.halt(500, 'no node ip get')
    }

    def appId = InMemoryAllContainerManager.instance.getAppIpByContainerId(id)
    User user = req.session('user')

    if (!appId && !user.isAdmin()) {
        resp.halt(403, 'not admin')
    }
    if (appId && !user.isAccessApp(appId)) {
        resp.halt(403, 'not this app manager')
    }

    if (appId && scriptName == 'container stop') {
        def app = new AppDTO(id: appId).one() as AppDTO
        if (app.gatewayConf) {
            def r = AgentCaller.instance.agentScriptExe(nodeIp, 'container inspect', [id: id])
            def containerInspectInfo = r.getJSONObject('container')
            def result = new ContainerRunResult(containerConfig: containerInspectInfo)
            result.extract(app.gatewayConf)

            def nodeIpDockerHost = InMemoryAllContainerManager.instance.getNodeIpDockerHost(nodeIp)
            GatewayOperator.create(appId, app.gatewayConf).removeBackend(nodeIpDockerHost, result.port)
        }
    }

    def lock = SpiSupport.createLock()
    lock.lockKey = 'operate app ' + (appId ?: user.name)
    boolean isDone = lock.exe {
        def r = AgentCaller.instance.agentScriptExe(nodeIp, scriptName, [id: id, readTimeout: 1000 * 10])
        resp.end r.toJSONString()
    }
    if (!isDone) {
        resp.json([error: 'get lock fail'])
    }
}

h.post('/api/container/create/tpl') { req, resp ->
    /*
    def content = Agent.instance.post('/dms/api/container/create/tpl',
            [clusterId           : createConf.clusterId,
             appId               : createConf.appId,
             appIdList           : createConf.appIdList,
             nodeIp              : createConf.nodeIp,
             nodeIpDockerHost    : createConf.nodeIpDockerHost,
             nodeIpList          : createConf.nodeIpList,
             nodeIpDockerHostList: createConf.nodeIpDockerHostList,
             targetNodeIpList    : createConf.conf.targetNodeIpList,
             instanceIndex       : createConf.instanceIndex,
             containerNumber     : conf.containerNumber,
             allAppLogDir        : createConf.globalEnvConf.allAppLogDir,
             imageTplId          : one.imageTplId], String)
     */
    HashMap map = req.bodyAs()
    int clusterId = map.clusterId
    int appId = map.appId
    int imageTplId = map.imageTplId

    def app = new AppDTO(id: appId).one() as AppDTO
    def tplOne = new ImageTplDTO(id: imageTplId).one() as ImageTplDTO
    def paramList = app.conf.fileVolumeList.find { it.imageTplId == imageTplId }.paramList
    paramList.each {
        map[it.key] = it.value
    }
    map.conf = app.conf
    map.applications = ContainerMountFileGenerator.prepare(User.Admin, clusterId)

    def appMonitorList = new AppDTO(clusterId: clusterId).
            queryFields('id,cluster_id,status,monitor_conf').loadList() as List<AppDTO>
    map.appMonitorList = appMonitorList.findAll { it.autoManage() && it.monitorConf }

    def content = CachedGroovyClassLoader.instance.eval(tplOne.content, map)
    resp.end content
}

h.group('/container') {
    h.get('/list') { req, resp ->
        def appId = req.param('appId')
        assert appId

        def app = new AppDTO(id: appId as int).one() as AppDTO
        assert app

        User user = req.session('user')
        def containerList = InMemoryAllContainerManager.instance.getContainerList(app.clusterId, app.id, null, user) ?: []
        def simpleContainerList = containerList.collect { x ->
            def r = new JSONObject(x)
            r.remove('Mounts')
            r.remove('Names')
            r.remove('NetworkSettings')
            r.remove('Labels')
            r
        }
        simpleContainerList
    }.get('/log') { req, resp ->
        def id = req.param('id')
        assert id
        def nodeIp = InMemoryAllContainerManager.instance.getNodeIpByContainerId(id)
        if (!nodeIp) {
            resp.halt(500, 'no node ip get')
        }

        def since = req.param('since')
        def tail = req.param('tail')
        def r = AgentCaller.instance.agentScriptExeBody(nodeIp, 'container log viewer',
                [id: id, since: since, tail: tail, isBodyRaw: 1])
        resp.end r
    }
}
