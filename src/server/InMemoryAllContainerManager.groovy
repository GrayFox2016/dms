package server

import auth.Permit
import auth.PermitType
import auth.User
import com.alibaba.fastjson.JSONObject
import common.ContainerHelper
import common.IntervalJob
import common.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.AppDTO
import model.NodeDTO

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
@Singleton
@Slf4j
class InMemoryAllContainerManager extends IntervalJob implements AllContainerManager {

    private ConcurrentHashMap<String, List<JSONObject>> containersByNodeIp = new ConcurrentHashMap()
    private ConcurrentHashMap<String, JSONObject> nodeInfoByNodeIp = new ConcurrentHashMap()
    private ConcurrentHashMap<String, Date> heartBeatDateByNodeIp = new ConcurrentHashMap()
    private ConcurrentHashMap<String, String> authTokenByNodeIp = new ConcurrentHashMap()

    void addAuthToken(String nodeIp, String authToken) {
        authTokenByNodeIp[nodeIp] = authToken
    }

    // refer Agent.sendNode
    void addNodeInfo(String nodeIp, JSONObject node) {
        heartBeatDateByNodeIp[nodeIp] = new Date()
        nodeInfoByNodeIp[nodeIp] = node
    }

    void addContainers(String nodeIp, List<JSONObject> containers) {
        heartBeatDateByNodeIp[nodeIp] = new Date()
        containersByNodeIp[nodeIp] = containers
    }

    @Override
    String name() {
        'memory container manager'
    }

    @Override
    void doJob() {
        // remove 1 hour ago
        def dat = Utils.getNodeAliveCheckLastDate(6 * 60)
        heartBeatDateByNodeIp.findAll { k, v ->
            v < dat
        }.each { k, v ->
            authTokenByNodeIp.remove(k)
            nodeInfoByNodeIp.remove(k)
            containersByNodeIp.remove(k)
            heartBeatDateByNodeIp.remove(k)
            log.info('done remove heart beat too old node - ' + k)
        }
    }

    Date getHeartBeatDate(String nodeIp) {
        heartBeatDateByNodeIp[nodeIp]
    }

    String getAuthToken(String nodeIp) {
        authTokenByNodeIp[nodeIp]
    }

    JSONObject getNodeInfo(String nodeIp) {
        nodeInfoByNodeIp[nodeIp]
    }

    String getNodeIpDockerHost(String nodeIp) {
        def obj = nodeInfoByNodeIp[nodeIp]
        if (!obj) {
            return nodeIp
        }

        obj.getString('nodeIpDockerHost') ?: nodeIp
    }

    Map<String, JSONObject> getAllNodeInfo() {
        new HashMap<String, JSONObject>(nodeInfoByNodeIp)
    }

    String getNodeIpByContainerId(String containerId) {
        for (entry in containersByNodeIp) {
            def nodeIp = entry.key
            def list = entry.value
            for (x in list) {
                if (containerId == ContainerHelper.getContainerId(x)) {
                    return nodeIp
                }
            }
        }
        null
    }

    Integer getAppIpByContainerId(String containerId) {
        for (entry in containersByNodeIp) {
            def list = entry.value
            for (x in list) {
                if (containerId == ContainerHelper.getContainerId(x)) {
                    return ContainerHelper.getAppId(x)
                }
            }
        }
        null
    }

    List<NodeDTO> getHeartBeatOkNodeList(int clusterId) {
        def dat = Utils.getNodeAliveCheckLastDate(3)
        def r = new NodeDTO().where('cluster_id = ?', clusterId).
                where('updated_date > ?', dat).loadList() as List<NodeDTO>
        r.sort { a, b -> Utils.compareIp(a.ip, b.ip) }
        r
    }

    List<JSONObject> getContainerList(int clusterId, int appId = 0,
                                      String nodeIp = null, User user = null) {
        List<JSONObject> r = []
        containersByNodeIp.each { k, v ->
            if (nodeIp) {
                if (nodeIp == k) {
                    r.addAll(v)
                }
            } else {
                r.addAll(v)
            }
        }
        if (!r) {
            return r
        }

        if (user) {
            def userAccessAppIdSet = new HashSet<Integer>()
            user.permitList.each {
                if (it.type == PermitType.cluster) {
                    def list = new AppDTO(clusterId: it.id).queryFields('id').loadList() as List<AppDTO>
                    userAccessAppIdSet.addAll(list.collect { it.id })
                }
                if (it.type == PermitType.namespace) {
                    def list = new AppDTO(namespaceId: it.id).queryFields('id').loadList() as List<AppDTO>
                    userAccessAppIdSet.addAll(list.collect { it.id })
                }
            }
            if (userAccessAppIdSet) {
                for (userAccessAppId in userAccessAppIdSet) {
                    user.permitList << new Permit(PermitType.app, userAccessAppId)
                }
            }
        }

        def list = r.findAll { x ->
            def appIdTarget = ContainerHelper.getAppId(x)
            if (appId && appIdTarget != appId) {
                return false
            }
            if (ContainerHelper.getClusterId(x) != clusterId) {
                return false
            }
            if (user && appIdTarget && !user.isAccessApp(appIdTarget)) {
                return false
            }
            true
        }
        list.sort { x, y ->
            def appIdX = ContainerHelper.getAppId(x)
            def appIdY = ContainerHelper.getAppId(y)
            if (appIdX == appIdY) {
                def instanceIndexX = ContainerHelper.getAppInstanceIndex(x)
                def instanceIndexY = ContainerHelper.getAppInstanceIndex(y)
                if (instanceIndexX == instanceIndexY) {
                    return 0
                } else {
                    if (instanceIndexX == null) {
                        return -1
                    } else if (instanceIndexY == null) {
                        return 1
                    } else {
                        return instanceIndexX - instanceIndexY;
                    }
                }
            } else {
                if (appIdX == null) {
                    return -1
                } else {
                    return 1
                }
            }
        }
    }
}
