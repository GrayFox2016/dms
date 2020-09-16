package script

import agent.Agent
import com.spotify.docker.client.DockerClient
import common.Conf
import common.Event
import common.Utils
import model.json.FileVolumeMount
import org.apache.commons.io.FileUtils
import org.segment.d.json.JsonReader
import org.segment.web.common.CachedGroovyClassLoader
import org.slf4j.LoggerFactory
import server.scheduler.CreateContainerConf

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')

String containerId = params.containerId
String jsonStr = params.jsonStr
if (!containerId || !jsonStr) {
    return [error: 'containerId and jsonStr required']
}

def createConf = JsonReader.instance.read(jsonStr, CreateContainerConf)
def conf = createConf.conf

def log = LoggerFactory.getLogger(this.getClass())

conf.fileVolumeList.findAll { it.isReloadInterval }.each { FileVolumeMount one ->
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

    if (one.isParentDirMount == 1) {
        String fileLocal = Conf.isWindows() ? one.dist.replace('/c/Users/', 'C:/Users/') : one.dist
        // dyn
        String hostFileFinal
        if (fileLocal.contains('${')) {
            hostFileFinal = CachedGroovyClassLoader.instance.eval('"' + fileLocal + '"', [appId: createConf.appId, instanceIndex: createConf.instanceIndex])
        } else {
            hostFileFinal = fileLocal
        }

        def localFile = new File(hostFileFinal)
        if (localFile.exists() && localFile.text == content) {
            // skip
            log.info 'skip file volume reload  - ' + hostFileFinal
        } else {
            FileUtils.forceMkdirParent(localFile)
            localFile.text = content

            Agent.instance.addEvent Event.builder().type(Event.Type.node).reason('file volume reload').result('app ' + createConf.appId).
                    build().log(content)
        }
    } else {
        def container = docker.inspectContainer(containerId)
        def mount = container.mounts().find { it.destination() == one.dist }
        if (mount) {
            String hostFilePath = mount.source()
            String fileLocal = Conf.isWindows() ? hostFilePath.replace('/c/Users/', 'C:/Users/') : hostFilePath

            def localFile = new File(fileLocal)
            if (localFile.exists() && localFile.text == content) {
                // skip
                log.info 'skip file volume reload  - ' + hostFilePath
            } else {
                FileUtils.forceMkdirParent(localFile)
                localFile.text = content
                Utils.setFileRead(localFile)

                Agent.instance.addEvent Event.builder().type(Event.Type.node).reason('file volume reload').result('app ' + createConf.appId).
                        build().log(content)
            }
        }
    }
}

[flag: true]