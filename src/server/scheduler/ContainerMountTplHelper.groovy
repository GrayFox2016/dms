package server.scheduler

import com.alibaba.fastjson.JSONObject
import common.ContainerHelper
import groovy.transform.CompileStatic
import model.AppDTO

@CompileStatic
class ContainerMountTplHelper {
    static class OneApp {
        Integer appId

        List<JSONObject> containerList

        OneApp(Integer appId, List<JSONObject> containerList) {
            this.appId = appId
            this.containerList = containerList

            app = new AppDTO(id: appId).one()
        }

        AppDTO app

        int getPublicPortByPrivatePort(int privatePort) {
            ContainerHelper.getPublicPort(privatePort, containerList[0])
        }

        List<String> getAllNodeIpList() {
            def targetNodeIpList = app.conf.targetNodeIpList
            if (targetNodeIpList) {
                return targetNodeIpList
            }

            containerList.collect { x ->
                ContainerHelper.getNodeIp(x)
            }
        }

        List<String> getAllHostnameList() {
            def containerNumber = app.conf.containerNumber
            (0..<containerNumber).collect {
                ContainerHelper.generateContainerHostname(appId, it)
            }
        }
    }

    private Map<Integer, List<JSONObject>> groupByApp

    private List<OneApp> list = []

    ContainerMountTplHelper(Map<Integer, List<JSONObject>> groupByApp) {
        this.groupByApp = groupByApp
        groupByApp.each { k, v ->
            list << new OneApp(k, v)
        }
    }

    OneApp app(String name) {
        list.find { it.app?.name == name }
    }
}
