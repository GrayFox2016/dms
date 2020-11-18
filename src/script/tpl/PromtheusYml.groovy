package script.tpl

import common.ContainerHelper
import model.AppDTO
import server.InMemoryAllContainerManager

def list = []

List<AppDTO> appMonitorList = super.binding.getProperty('appMonitorList')
appMonitorList.each { app ->
    def monitorConf = app.monitorConf
    def containerList = InMemoryAllContainerManager.instance.getContainerList(app.clusterId, app.id)

    Set<String> set = []
    containerList.collect { x ->
//        def instanceIndex = ContainerHelper.getAppInstanceIndex(x)
        def nodeIpDockerHost = ContainerHelper.getNodeIpDockerHost(x)
        def publicPort = ContainerHelper.getPublicPort(monitorConf.port, x)
        set << "'${nodeIpDockerHost}:${publicPort}'"
    }
    String inner = set.join(',')
    list << """
  - job_name: app_${app.id}
    static_configs:
      - targets: [${inner}]
"""
}


"""
global:
  scrape_interval:     15s
  evaluation_interval: 15s

scrape_configs:
${list.join("\r\n")}
"""