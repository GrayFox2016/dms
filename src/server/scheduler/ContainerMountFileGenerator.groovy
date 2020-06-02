package server.scheduler

import auth.User
import com.alibaba.fastjson.JSONObject
import common.ContainerHelper
import groovy.transform.CompileStatic
import server.InMemoryAllContainerManager

@CompileStatic
class ContainerMountFileGenerator {

    static ContainerMountTplHelper prepare(User user, int clusterId) {
        def containerList = InMemoryAllContainerManager.instance.getContainerList(clusterId, 0, null, user)
        Map<Integer, List<JSONObject>> groupByApp = containerList.groupBy { x ->
            ContainerHelper.getAppId(x)
        }
        new ContainerMountTplHelper(groupByApp)
    }
}
