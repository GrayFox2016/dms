package model.json

import groovy.transform.CompileStatic
import org.apache.commons.beanutils.BeanUtils
import org.segment.d.BeanReflector
import org.segment.d.json.JSONFiled
import org.segment.d.json.JsonWriter

@CompileStatic
class AppConf implements JSONFiled {
    int registryId

    String group

    String image

    String tag

    String cmd

    String user

    int memMB = 1024

    int cpuShare = 1024

    double cpuFixed = 1.0

    int containerNumber = 1

    boolean isParallel = false

    List<String> targetNodeTagList = []

    List<String> targetNodeIpList = []

    List<String> excludeNodeTagList = []

    boolean isPrivileged = false

    List<KVPair> envList = []

    List<ULimit> uLimitList = []

    List<DirVolumeMount> dirVolumeList = []

    List<FileVolumeMount> fileVolumeList = []

    String networkMode

    boolean isNetworkDnsUsingCluster = true

    List<PortMapping> portList = []

    AppConf copy() {
        def r = new AppConf()
        BeanUtils.copyProperties(r, this)
        r
    }

    @Override
    boolean equals(Object obj) {
        if (!(obj instanceof AppConf)) {
            return false
        }
        def fields = BeanReflector.getClassFields(AppConf).collect { it.name }
        fields.remove('containerNumber')
        def that = this
        def other = (AppConf) obj
        fields.every {
            that.getProperty(it) == other.getProperty(it)
        }
    }

    @Override
    String toString() {
        JsonWriter.instance.json(this)
    }
}
