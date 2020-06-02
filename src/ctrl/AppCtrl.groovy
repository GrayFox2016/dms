package ctrl

import auth.User
import com.alibaba.fastjson.JSON
import common.ContainerHelper
import common.Event
import model.*
import org.segment.web.handler.ChainHandler
import org.slf4j.LoggerFactory
import server.InMemoryAllContainerManager
import server.hpa.ScaleRequest
import server.hpa.ScaleRequestHandler

def h = ChainHandler.instance

def log = LoggerFactory.getLogger(this.getClass())

h.get('/api/app/scale') { req, resp ->
    def appId = req.param('appId')
    def scaleCmd = req.param('scaleCmd')
    def nodeIp = req.param('nodeIp')

    Event.builder().type(Event.Type.cluster).reason('trigger scale').result('' + appId).
            build().log(nodeIp + ' - ' + scaleCmd).toDto().add()
    ScaleRequestHandler.instance.add(appId as int, new ScaleRequest(nodeIp: nodeIp, scaleCmd: scaleCmd as int))
    [flag: true]
}

h.get('/api/image/pull/hub/info') { req, resp ->
    def registryId = req.param('registryId')
    assert registryId
    new ImageRegistryDTO(id: registryId as int).one()
}

h.group('/app') {
    h.group('/option') {
        h.get('/list') { req, resp ->
            def clusterList = new ClusterDTO().where('1=1').queryFields('id,name').loadList()
            def namespaceList = new NamespaceDTO().where('1=1').queryFields('id,cluster_id,name').loadList()
            def registryList = new ImageRegistryDTO().where('1=1').queryFields('id,url').loadList()
            def nodeList = new NodeDTO().where('1=1').queryFields('ip,tags,clusterId').loadList() as List<NodeDTO>
            def nodeIpList = nodeList.collect { [ip: it.ip, clusterId: it.clusterId] }

            Set nodeTagSet = []
            nodeList.each {
                def tags = it.tags
                if (tags) {
                    tags.split(',').each { tag ->
                        nodeTagSet << '' + it.clusterId + ',' + tag
                    }
                }
            }
            def nodeTagList = nodeTagSet.collect {
                def arr = it.split(',')
                [clusterId: arr[0] as int, tag: arr[1]]
            }

            [clusterList: clusterList, namespaceList: namespaceList, registryList: registryList,
             nodeIpList : nodeIpList, nodeTagList: nodeTagList]
        }.get('/image/env/list') { req, resp ->
            def image = req.param('image')
            assert image
            new ImageEnvDTO(imageName: image).queryFields('env,name').loadList()
        }.get('/image/port/list') { req, resp ->
            def image = req.param('image')
            assert image
            new ImagePortDTO(imageName: image).queryFields('port,name').loadList()
        }.get('/image/tpl/list') { req, resp ->
            def image = req.param('image')
            assert image
            new ImageTplDTO(imageName: image, tplType: ImageTplDTO.TplType.mount).loadList()
        }.get('/image/volume/list') { req, resp ->
            def clusterId = req.param('clusterId')
            assert clusterId
            new NodeVolumeDTO(clusterId: clusterId as int).queryFields('id,dir,name').loadList()
        }
    }

    h.get('/list') { req, resp ->
        def clusterId = req.param('clusterId')
        def namespaceId = req.param('namespaceId')
//        assert clusterId || namespaceId

        def p = req.param('pageNum')
        int pageNum = p ? p as int : 1
        final int pageSize = 10

        def keyword = req.param('keyword')
        new AppDTO().where('1=1').where(!!clusterId && !namespaceId, 'cluster_id = ?', clusterId).
                where(!!namespaceId, 'namespace_id = ?', namespaceId).
                where(!!keyword, '(name like ?) or (des like ?)',
                        '%' + keyword + '%', '%' + keyword + '%').loadPager(pageNum, pageSize)
    }.get('/list/simple') { req, resp ->
        def namespaceId = req.param('namespaceId')
        assert namespaceId
        new AppDTO(namespaceId: namespaceId as int).loadList()
    }.delete('/delete/:id') { req, resp ->
        def id = req.param(':id')
        assert id

        AppDTO one = new AppDTO(id: id as int).queryFields('id,cluster_id,namespace_id').one()
        User u = req.session('user')
        if (!u.isAccessNamespace(one.namespaceId)) {
            resp.halt(500, 'not this namespace manager')
        }

        // check if container running
        def list = InMemoryAllContainerManager.instance.getContainerList(one.clusterId, one.id)
        if (list) {
            resp.halt(500, 'this app has containers')
        }

        def appJobList = new AppJobDTO(appId: one.id).queryFields('id').loadList() as List<AppJobDTO>
        if (appJobList) {
            new AppJobLogDTO().whereIn('app_id', appJobList.collect { it.appId }).deleteAll()
            new AppJobDTO(appId: one.id).deleteAll()
        }
        new AppDTO(id: one.id).delete()
        [flag: true]
    }.post('/update') { req, resp ->
        def one = req.bodyAs(AppDTO)
        assert one.name && one.namespaceId

        boolean isLimitNode = one.conf.envList.any { it.key?.contains('X_LIMIT_NODE') }
        def nodeList = InMemoryAllContainerManager.instance.getHeartBeatOkNodeList(one.clusterId)
        if (one.conf.containerNumber > nodeList.size() && !isLimitNode) {
            resp.halt(500, 'container number <= available node size - ' + nodeList.size())
        }
        if (one.conf.targetNodeIpList && one.conf.containerNumber > one.conf.targetNodeIpList.size()) {
            resp.halt(500, 'container number <= target node ip list size - ' + one.conf.targetNodeIpList.size())
        }

        one.updatedDate = new Date()
        if (one.id) {
            User u = req.session('user')
            if (!u.isAccessApp(one.id)) {
                resp.halt(500, 'not this app manager')
            }

            def oldOne = new AppDTO(id: one.id).one() as AppDTO
            def oldConf = oldOne.conf
            def conf = one.conf

            def isNeedScroll = conf != oldConf
            if (isNeedScroll) {
                if (oldConf.containerNumber != conf.containerNumber) {
                    resp.halt(500, 'change container number and change others at the same time not allowed')
                } else {
                    one.update()
                    log.info 'change from ' + oldConf + ' to ' + conf
                    def jobId = new AppJobDTO(appId: one.id, failNum: 0,
                            status: AppJobDTO.Status.created.val,
                            jobType: AppJobDTO.JobType.scroll.val,
                            createdDate: new Date(), updatedDate: new Date()).add()
                    return oldOne.autoManage() ? [id: one.id, jobId: jobId] : [id: one.id]
                }
            } else {
                if (oldConf.containerNumber == conf.containerNumber) {
                    one.update()
                    return [id: one.id]
                }

                boolean isAdd = conf.containerNumber > oldConf.containerNumber
                Map params
                if (isAdd) {
                    List<Integer> needRunInstanceIndexList = []

                    def containerList = InMemoryAllContainerManager.instance.getContainerList(one.clusterId, one.id)
                    (0..<conf.containerNumber).each { i ->
                        def runningOne = containerList.find { x ->
                            i == ContainerHelper.getAppInstanceIndex(x)
                        }
                        if (!runningOne) {
                            needRunInstanceIndexList << i
                        }
                    }
                    params = [needRunInstanceIndexList: needRunInstanceIndexList]
                } else {
                    params = [toContainerNumber: conf.containerNumber]
                }

                one.update()
                log.info 'scale from ' + oldConf.containerNumber + ' to ' + conf.containerNumber
                def jobType = isAdd ? AppJobDTO.JobType.create : AppJobDTO.JobType.remove
                def jobId = new AppJobDTO(appId: one.id, failNum: 0, jobType: jobType.val,
                        createdDate: new Date(), updatedDate: new Date(), params: JSON.toJSONString(params)).add()
                return oldOne.autoManage() ? [id: one.id, jobId: jobId] : [id: one.id]
            }
        } else {
            User u = req.session('user')
            if (!u.isAccessNamespace(one.namespaceId)) {
                resp.halt(500, 'not this namespace manager')
            }

            one.status = AppDTO.Status.auto.val
            def id = one.add()

            def jobId = new AppJobDTO(appId: one.id, failNum: 0, jobType: AppJobDTO.JobType.create.val,
                    createdDate: new Date(), updatedDate: new Date()).add()
            return [id: id, jobId: jobId]
        }
    }.get('/manual') { req, resp ->
        def id = req.param('id')
        assert id
        def one = new AppDTO(id: id as int).queryFields('id,status').one() as AppDTO

        one.status = one.status == AppDTO.Status.auto.val ? AppDTO.Status.manual.val : AppDTO.Status.auto.val
        one.update()
        [status: one.status]
    }

    // update monitor/live check/ab/job/gateway
    h.post('/conf/update') { req, resp ->
        def one = req.bodyAs(AppDTO)
        assert one.id

        User u = req.session('user')
        if (!u.isAccessApp(one.id)) {
            resp.halt(500, 'not this app manager')
        }

        one.updatedDate = new Date()
        one.update()
        return [id: one.id]
    }
}

h.group('/api/app') {
    h.post('/monitor-conf/query') { req, resp ->
        HashMap map = req.bodyAs()
        List<Integer> appIdList = map.appIdList
        def appList = new AppDTO().whereIn('id', appIdList).queryFields('id,monitor_conf').loadList() as List<AppDTO>
        appList.findAll {
            it.monitorConf
        }
    }.post('/live-check-conf/query') { req, resp ->
        HashMap map = req.bodyAs()
        List<Integer> appIdList = map.appIdList
        def appList = new AppDTO().whereIn('id', appIdList).queryFields('id,live_check_conf').loadList() as List<AppDTO>
        appList.findAll {
            it.liveCheckConf
        }
    }
}