package server.scheduler

import groovy.transform.CompileStatic
import model.json.AppConf
import model.json.GlobalEnvConf
import org.segment.d.json.JsonWriter

@CompileStatic
class CreateContainerConf {
    AppConf conf

    String nodeIp

    Integer appId

    Integer clusterId

    Integer instanceIndex

    List<String> nodeIpList

    List<Integer> appIdList

    GlobalEnvConf globalEnvConf

    String imageWithTag

    @Override
    String toString() {
        JsonWriter.instance.json(this)
    }
}
