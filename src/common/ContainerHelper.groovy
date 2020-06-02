package common

import com.alibaba.fastjson.JSONObject
import com.spotify.docker.client.messages.Container
import groovy.transform.CompileStatic

@CompileStatic
class ContainerHelper {
    static final String KEY_ID = 'Id'
    static final String KEY_NODE_IP = 'Node_Ip'
    static final String KEY_NODE_IP_DOCKER_HOST = 'Node_Ip_Docker_Host'
    static final String KEY_CLUSTER_ID = 'Cluster_Id'
    static final String KEY_APP_ID = 'App_Id'
    static final String KEY_INSTANCE_INDEX = 'Instance_Index'
    static final String CONTAINER_NAME_PRE = '/app_'
    static final String APP_NAME_OTHER = 'other'

    static String getAppName(JSONObject x) {
        List<String> names = x.getJSONArray('Names') as List<String>
        def one = names?.find { name -> name.contains(CONTAINER_NAME_PRE) }
        one ?: APP_NAME_OTHER
    }

    static String getAppName(Container container) {
        def one = container.names().find { name -> name.contains(CONTAINER_NAME_PRE) }
        one ?: APP_NAME_OTHER
    }

    static Integer getAppId(JSONObject x) {
        x.getInteger(KEY_APP_ID)
    }

    static String getContainerId(JSONObject x) {
        x.getString(KEY_ID)
    }

    static String getNodeIp(JSONObject x) {
        x.getString(KEY_NODE_IP)
    }

    static String getNodeIpDockerHost(JSONObject x) {
        x.getString(KEY_NODE_IP_DOCKER_HOST)
    }

    static Integer getAppInstanceIndex(JSONObject x) {
        x.getInteger(KEY_INSTANCE_INDEX)
    }

    static String resetAppId(JSONObject x) {
        def appName = getAppName(x)
        x.put(KEY_APP_ID, getAppIdByAppName(appName))
        x.put(KEY_INSTANCE_INDEX, getAppInstanceByAppName(appName))
    }

    static Integer getClusterId(JSONObject x) {
        x.getInteger(KEY_CLUSTER_ID)
    }

    static Integer getAppIdByAppName(String appName) {
        if (!appName || !appName.startsWith(CONTAINER_NAME_PRE)) {
            return 0
        }
        appName.split('_')[1] as int
    }

    static Integer getAppInstanceByAppName(String appName) {
        if (!appName || !appName.startsWith(CONTAINER_NAME_PRE)) {
            return -1
        }
        appName.split('_')[-1] as int
    }

    static String generateContainerName(int appId, int instanceIndex) {
        CONTAINER_NAME_PRE + appId + '_' + instanceIndex
    }

    static String generateContainerHostname(int appId, int instanceIndex) {
        generateContainerName(appId, instanceIndex)[1..-1]
    }

    static int getPublicPort(int privatePort, JSONObject x) {
        def ports = x.getJSONArray('Ports')
        if (!ports) {
            return privatePort
        }
        for (one in ports) {
            def item = one as JSONObject
            if (privatePort == item.getInteger('PrivatePort')) {
                return item.getInteger('PublicPort')
            }
        }
        privatePort
    }

    static int getPublicPort(int privatePort, Container container) {
        def one = container.ports().find {
            it.privatePort() == privatePort
        }
        one ? one.publicPort() : privatePort
    }

    static boolean isRunning(JSONObject x) {
        'running' == x.getString('State')
    }
}
