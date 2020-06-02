package server.scheduler

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import groovy.transform.CompileStatic
import model.json.GatewayConf

@CompileStatic
class ContainerRunResult {

    JobStepKeeper keeper

    String nodeIp

    int port

    JSONObject containerConfig

    void extract(GatewayConf gatewayConf) {
        extract(gatewayConf.containerPrivatePort)
    }

    void extract(Integer privatePort) {
        def hostConfig = containerConfig.getJSONObject('HostConfig')
        if ('host' == hostConfig.getString('NetworkMode')) {
            port = privatePort
            return
        }

        hostConfig.getJSONObject('PortBindings').each { k, v ->
            def arr = k.split(/\//)
            if (arr[0] as int != privatePort) {
                return
            }
            def jsonArr = v as JSONArray
            for (one in jsonArr) {
                def obj = one as JSONObject
                port = obj.getString('HostPort') as int
            }
        }
    }
}
