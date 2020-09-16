package script

import agent.Agent
import com.alibaba.fastjson.JSON
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import common.Conf
import common.Utils
import model.json.FileVolumeMount
import model.json.KVPair
import model.json.PortMapping
import org.apache.commons.io.FileUtils
import org.segment.d.json.JsonReader
import org.segment.web.common.CachedGroovyClassLoader
import server.scheduler.CreateContainerConf

import static common.ContainerHelper.*
import static java.nio.file.attribute.PosixFilePermission.*

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String jsonStr = params.jsonStr
if (!jsonStr) {
    return [error: 'jsonStr required']
}

def createConf = JsonReader.instance.read(jsonStr, CreateContainerConf)
def conf = createConf.conf

def hostConfigB = HostConfig.builder()
if (conf.isPrivileged) {
    hostConfigB.privileged(true)
}

def envList = conf.envList.findAll { it.key }
createConf.globalEnvConf.envList.each {
    envList << it
}
envList << new KVPair(key: KEY_APP_ID, value: createConf.appId)
envList << new KVPair(key: KEY_CLUSTER_ID, value: createConf.clusterId)
envList << new KVPair(key: KEY_NODE_IP, value: createConf.nodeIp)
envList << new KVPair(key: KEY_NODE_IP_LIST, value: createConf.nodeIpList.join(','))
envList << new KVPair(key: KEY_NODE_IP_DOCKER_HOST, value: createConf.nodeIpDockerHost)
envList << new KVPair(key: KEY_INSTANCE_INDEX, value: createConf.instanceIndex)

int vCpuNumber
if (conf.cpuShare) {
    hostConfigB.cpuShares(conf.cpuShare as long)
    vCpuNumber = (conf.cpuShare / 1024) as int
} else if (conf.cpuFixed) {
    long cpuPeriod = 100 * 1000
    long cpuQuota = (conf.cpuFixed * cpuPeriod) as long
    hostConfigB.cpuPeriod(cpuPeriod).cpuQuota(cpuQuota)
    vCpuNumber = Math.ceil(conf.cpuFixed).intValue()
}

long mb = (1024 * 1024) as long
long mem = conf.memMB * mb
hostConfigB.memory(mem)

envList << new KVPair(key: 'X_memory', value: mem)
envList << new KVPair(key: 'X_vCpuNumber', value: vCpuNumber)

// network *** ***
def networkMode = conf.networkMode ?: 'host'
hostConfigB.networkMode(networkMode)
boolean isNetworkHost = networkMode == 'host'

if (!isNetworkHost) {
    Map<String, List<PortBinding>> r = [:]
    conf.portList.eachWithIndex { PortMapping pm, int i ->
        def publicPort = pm.publicPort == -1 ? Utils.getOnePortListenAvailable() : pm.publicPort
        envList << new KVPair(key: 'X_port_' + pm.privatePort, value: '' + publicPort)
        r['' + pm.privatePort + '/' + pm.listenType] = [PortBinding.create('', '' + publicPort)]
    }
    hostConfigB.portBindings(r)
} else {
    // for ssh server / web shell
    if (envList.any { it.key.contains('DYN_SSH_LISTEN_PORT') }) {
        envList << new KVPair(key: 'SSH_SERVER_PORT', value: Utils.getOnePortListenAvailable())
    }
}

// dns *** ***
List<String> dnsServerList = []
if (conf.isNetworkDnsUsingCluster && createConf.globalEnvConf.dnsServer) {
    createConf.globalEnvConf.dnsServer.split(',').each {
        dnsServerList << it
    }
}
if (!Conf.isWindows()) {
    def f = new File('/etc/resolv.conf')
    if (f.exists() && f.canRead()) {
        f.readLines().findAll { it.contains('nameserver') }.each {
            dnsServerList << it.trim().split(' ')[1].trim()
        }
    }
}
if (dnsServerList) {
    hostConfigB.dns(dnsServerList)
}

// volume *** ***
List<String> binds = []

String tplConfFileDir = Conf.isWindows() ? 'userHome/volume' :
        Conf.instance.getString('tplConfFileDir', '/opt/config')
conf.fileVolumeList.eachWithIndex { FileVolumeMount one, int i ->
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
        FileUtils.forceMkdirParent(localFile)
        localFile.text = content
    } else {
        String hostFilePath = (Conf.isWindows() ? tplConfFileDir.replace('userHome', System.getProperty('user.home')) : tplConfFileDir) +
                ('/' + createConf.appId + '/' + Utils.uuid() + '.file')
        def localFile = new File(hostFilePath)
        FileUtils.forceMkdirParent(localFile)
        localFile.text = content
        Utils.setFileRead(localFile)

        String mountHostFilePath = Conf.isWindows() ? hostFilePath.replace('C:\\Users\\', '/c/Users/') : hostFilePath
        binds << "${mountHostFilePath}:${one.dist}:rw".toString()
    }
}

conf.dirVolumeList.each {
    String mod = 'rw'
    boolean needChangeMode = true

    String dirMount = it.dir
    String dirLocal = Conf.isWindows() ? it.dir.replace('/c/Users/', 'C:/Users/') : it.dir
    // dyn
    String dirMountFinal
    String hostDirFinal
    if (dirLocal.contains('${')) {
        dirMountFinal = CachedGroovyClassLoader.instance.eval('"' + dirMount + '"', [appId: createConf.appId, instanceIndex: createConf.instanceIndex])
        hostDirFinal = CachedGroovyClassLoader.instance.eval('"' + dirLocal + '"', [appId: createConf.appId, instanceIndex: createConf.instanceIndex])
    } else {
        dirMountFinal = dirMount
        hostDirFinal = dirLocal
    }

    def dir = new File(hostDirFinal)
    if (!dir.exists()) {
        FileUtils.forceMkdir(dir)
    } else {
        if (dir.isFile()) {
            mod = 'ro'
            needChangeMode = false
        }
    }

    if (needChangeMode) {
        Utils.setFilePermission(dir, OWNER_READ, OWNER_EXECUTE, OWNER_WRITE,
                GROUP_READ, GROUP_EXECUTE, GROUP_WRITE, OTHERS_READ, OTHERS_EXECUTE, OTHERS_WRITE)
    }

    if (dirMountFinal == createConf.globalEnvConf.allAppLogDir && !conf.image.contains('filebeat')) {
        binds << "${dirMountFinal}/${createConf.appId}:${it.dist}:${mod}".toString()
    } else {
        binds << "${dirMountFinal}:${it.dist}:${mod}".toString()
    }
}

hostConfigB.binds(binds)

// uLimit *** ***
List<HostConfig.Ulimit> uLimitList = conf.uLimitList.collect {
    HostConfig.Ulimit.builder().name(it.name).soft(it.soft).hard(it.hard).build()
}
if (uLimitList) {
    hostConfigB.ulimits(uLimitList)
}

def b = ContainerConfig.builder().user(conf.user ?: 'root').image(createConf.imageWithTag).
        hostConfig(hostConfigB.build()).env(envList.collect {
    "${it.key}=${it.value.toString()}".toString()
})

if (!isNetworkHost) {
    b.hostname(generateContainerHostname(createConf.appId, createConf.instanceIndex))
}

// cmd *** ***
if (conf.cmd) {
    List<String> cmd = []
    // json array
    if (conf.cmd.startsWith('[')) {
        JSON.parseArray(conf.cmd).each {
            cmd << it.toString()
        }
    } else {
        cmd << "sh"
        cmd << "-c"
        cmd << conf.cmd
    }
    b.cmd(cmd)
}

def containerConfig = b.build()
String containerName = generateContainerName(createConf.appId, createConf.instanceIndex)[1..-1]
def creation = docker.createContainer(containerConfig, containerName)
Agent.instance.sendContainer()

[containerConfig: containerConfig, containerId: creation.id()]