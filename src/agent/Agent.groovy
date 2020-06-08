package agent

import agent.script.ScriptHolder
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.github.kevinsawicki.http.HttpRequest
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.Container
import common.Event
import common.IntervalJob
import common.LimitQueue
import common.Utils
import ex.HttpInvokeException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.json.LiveCheckConf
import model.json.MonitorConf
import org.hyperic.sigar.Sigar
import org.segment.web.common.CachedGroovyClassLoader
import org.segment.web.handler.JsonWriter

import static common.ContainerHelper.*

@CompileStatic
@Singleton
@Slf4j
class Agent extends IntervalJob {
    String serverUrl
    int clusterId
    String secret
    String version = '1.0'

    int connectTimeout = 500
    int readTimeout = 2000

    final static String AUTH_TOKEN_HEADER = 'X-Auth-Token'
    final static String CLUSTER_ID_HEADER = 'X-Cluster-Id'

    String authToken

    DockerClient docker

    String nodeIp

    String nodeIpDockerHost

    Sigar sigar

    Set<String> scriptAgentCollectSet = new HashSet<>()

    private ContainerStatCollector collector = ContainerStatCollector.instance

    LimitQueue<Event> eventQueue = new LimitQueue<>(100)

    void addEvent(Event event) {
        event.createdDate = new Date()
        eventQueue << event
    }

    boolean isSendNodeInfoOk = true

    boolean isSendContainerInfoOk = true

    boolean isLiveCheckOk = true

    boolean isMetricGetOk = true

    public <T> T get(String uri, Map<String, Object> params = null, Class<T> clz = String,
                     Closure<Void> failCallback = null) {
        def req = HttpRequest.get(serverUrl + uri, params ?: [:], true).
                connectTimeout(connectTimeout).readTimeout(readTimeout).
                header(AUTH_TOKEN_HEADER, authToken ?: '').
                header(CLUSTER_ID_HEADER, clusterId)
        def body = req.body()
        if (req.code() != 200) {
            if (failCallback) {
                log.warn('agent get server info fail - ' + uri + ' - ' + params + ' - ' + body)
                failCallback.call(body)
            } else {
                throw new HttpInvokeException('agent get server info fail - ' + uri + ' - ' + params + ' - ' + body)
            }
        }
        if (clz == String) {
            return body as T
        }
        JSON.parseObject(body, clz)
    }

    public <T> T post(String uri, Map params, Class<T> clz = String,
                      Closure failCallback = null) {
        def req = HttpRequest.post(serverUrl + uri).
                connectTimeout(connectTimeout).readTimeout(readTimeout).
                header(AUTH_TOKEN_HEADER, authToken ?: '').
                header(CLUSTER_ID_HEADER, clusterId)
        def sendBody = JsonWriter.instance.json(params)
        def body = req.send(sendBody).body()
        if (req.code() != 200) {
            if (failCallback) {
                log.warn('agent post server info fail - ' + uri + ' - ' + sendBody + ' - ' + body)
                failCallback.call(body)
            } else {
                throw new HttpInvokeException('agent post server info fail - ' + uri + ' - ' + sendBody + ' - ' + body)
            }
        }
        if (clz == String) {
            return body as T
        }
        JSON.parseObject(body, clz)
    }

    void auth() {
        Map<String, Object> p = [:]
        p.secret = secret
        authToken = get('/dms/agent/auth', p)
    }

    void sendNode() {
        if (!scriptAgentCollectSet) {
            return
        }

        Map r = [:]
        r.nodeIp = nodeIp
        r.nodeIpDockerHost = nodeIpDockerHost
        r.clusterId = clusterId
        r.version = version
        r.isLiveCheckOk = isLiveCheckOk
        r.isMetricGetOk = isMetricGetOk

        Map<String, Object> variables = [:]
        variables.sigar = sigar
        variables.docker = docker

        for (scriptName in scriptAgentCollectSet) {
            def content = ScriptHolder.instance.getContentByName(scriptName)
            if (content) {
                Map evalR = CachedGroovyClassLoader.instance.eval(content, variables) as Map
                if (evalR) {
                    r.putAll(evalR)
                }
            }
        }
        isSendNodeInfoOk = true
        post('/dms/api/hb/node', r, String) { body ->
            isSendNodeInfoOk = false
        }
    }

    List<Container> sendContainer() {
        def containers = docker.listContainers(DockerClient.ListContainersParam.allContainers())

        Map r = [:]
        r.nodeIp = nodeIp
        r.nodeIpDockerHost = nodeIpDockerHost
        r.clusterId = clusterId
        r.containers = JsonWriter.instance.json(containers)

        isSendContainerInfoOk = true
        post('/dms/api/hb/container', r, String) { body ->
            isSendContainerInfoOk = false
        }
        containers
    }

    private void liveCheckAndCollectMetric(List<Container> containers) {
        def runningList = containers.findAll { it.state() == 'running' }
        List<OneContainer> containerList = runningList.findAll {
            def name = getAppName(it)
            name != APP_NAME_OTHER
        }.collect {
            def name = getAppName(it)
            new OneContainer(appId: getAppIdByAppName(name), instanceIndex: getAppInstanceByAppName(name))
        }
        if (!containerList) {
            isLiveCheckOk = true
            isMetricGetOk = true
            return
        }

        Map<Integer, LiveCheckConf> confMap = [:]
        String body = post('/dms/api/app/live-check-conf/query', [appIdList: containerList.collect { it.appId }])
        def arr = JSON.parseArray(body)
        for (one in arr) {
            def obj = one as JSONObject
            confMap[obj.getInteger('id')] = JSON.parseObject(obj.getJSONObject('liveCheckConf').toString(), LiveCheckConf)
        }

        Map<Integer, Boolean> liveCheckResult = [:]
        confMap.each { k, v ->
            def container = runningList.find {
                k == getAppIdByAppName(getAppName(it))
            }
            if (!container) {
                return
            }
            liveCheckResult[k] = liveCheck(k, container, v)
        }
        isLiveCheckOk = liveCheckResult.every { it.value }

        Map<Integer, MonitorConf> confMapMonitor = [:]
        String bodyMonitor = post('/dms/api/app/monitor-conf/query', [appIdList: containerList.collect { it.appId }])
        def arrMonitor = JSON.parseArray(bodyMonitor)
        for (one in arrMonitor) {
            def obj = one as JSONObject
            confMapMonitor[obj.getInteger('id')] = JSON.parseObject(obj.getJSONObject('monitorConf').toString(), MonitorConf)
        }

        Map<Integer, Boolean> monitorGetResult = [:]
        confMapMonitor.each { k, v ->
            def container = runningList.find {
                k == getAppIdByAppName(getAppName(it))
            }
            if (!container) {
                return
            }

            monitorGetResult[k] = metricGet(k, containerList.find { it.appId == k }.instanceIndex, container, v)
        }
        isMetricGetOk = monitorGetResult.every { it.value }
    }

    boolean metricGet(int appId, int instanceIndex, Container container, MonitorConf conf) {
        def decimal = conf.intervalSeconds / interval
        def ceil = Math.ceil(decimal.doubleValue()) as int
        if (intervalCount % ceil != 0) {
            return true
        }

        collector.collect(container.id(), appId, instanceIndex, conf)

        try {
            String bodyMetric
            if (conf.isShellScript) {
                def shellScript = conf.shellScript
                addEvent Event.builder().type(Event.Type.node).reason('metric get').result('' + appId).
                        build().log(shellScript)
                if (!shellScript) {
                    return false
                }

                List<String> shellResultList = []
                for (line in shellScript.readLines()) {
                    String cmdLine = line.trim()
                    String[] cmd = ['sh', '-c', cmdLine]
                    def exe = docker.execCreate(container.id(), cmd, DockerClient.ExecCreateParam.attachStdout(),
                            DockerClient.ExecCreateParam.attachStderr())
                    def stream = docker.execStart(exe.id())
                    String shellResult = stream.readFully()?.trim()
                    if (!shellResult) {
                        addEvent Event.builder().type(Event.Type.node).reason('metric get fail').result('' + appId).
                                build().log(cmdLine)
                        return false
                    }
                    shellResultList << shellResult
                }
                bodyMetric = shellResultList.join("\r\n")
            } else if (conf.isHttpRequest) {
                def publicPort = getPublicPort(conf.port, container)
                // eg. /health
                def uri = conf.httpRequestUri
                String url = 'http://127.0.0.1:' + publicPort + uri
                addEvent Event.builder().type(Event.Type.node).reason('metric get').result('' + appId).
                        build().log('http pull ' + url)

                def req = HttpRequest.get(url)
                def timeout = conf.httpTimeoutSeconds * 1000
                def code = req.connectTimeout(timeout).readTimeout(timeout).code()
                if (code != 200) {
                    addEvent Event.builder().type(Event.Type.node).reason('metric get fail').result('' + appId).
                            build().log('http pull ' + url + req.body())
                    return false
                }
                bodyMetric = req.body()
            } else {
                return true
            }

            Map gauges
            if (conf.metricFormatScriptContent) {
                Map<String, Object> variables = [:]
                variables.body = bodyMetric
                Map r = CachedGroovyClassLoader.instance.eval(conf.metricFormatScriptContent, variables) as Map
                if (r) {
                    gauges = r
                    AgentTempInfoHolder.instance.addAppMetric(appId, instanceIndex, gauges, bodyMetric)
                } else {
                    addEvent Event.builder().type(Event.Type.node).reason('metric get fail').result('' + appId).
                            build().log('metric body format - ' + conf.metricFormatScriptContent)
                    return false
                }
            } else {
                if (bodyMetric.startsWith('{')) {
                    gauges = JSON.parseObject(bodyMetric)
                    AgentTempInfoHolder.instance.addAppMetric(appId, instanceIndex, gauges, bodyMetric)
                } else {
                    gauges = [:]
                    bodyMetric.readLines().each {
                        def arr = it.split('=')
                        if (arr.length == 2) {
                            gauges[arr[0]] = arr[1]
                        }
                    }
                    AgentTempInfoHolder.instance.addAppMetric(appId, instanceIndex, gauges, bodyMetric)
                }
            }

            if (gauges && conf.isScaleAuto && !conf.isScaleDependOnCpuPerc) {
                def scaleCmd = gauges.get(MonitorConf.KEY_SCALE_OUT)
                if (scaleCmd) {
                    addEvent Event.builder().type(Event.Type.node).reason('trigger scale').result('' + appId).
                            build().log('' + scaleCmd)
                    get('/dms/api/app/scale', [nodeIp: nodeIp, appId: appId, scaleCmd: scaleCmd])
                }
            }
            true
        } catch (Exception e) {
            log.error('metric get error - ' + appId, e)
            return false
        }
    }

    boolean liveCheck(int appId, Container container, LiveCheckConf conf) {
        def decimal = conf.intervalSeconds / interval
        def ceil = Math.ceil(decimal.doubleValue()) as int
        if (intervalCount % ceil != 0) {
            return true
        }

        try {
            if (conf.isShellScript) {
                def shellScript = conf.shellScript
                addEvent Event.builder().type(Event.Type.node).reason('live check').result('' + appId).
                        build().log(shellScript)
                if (!shellScript) {
                    return false
                }

                for (line in shellScript.readLines()) {
                    String cmdLine = line.trim()
                    String[] cmd = ['sh', '-c', cmdLine]
                    def exe = docker.execCreate(container.id(), cmd, DockerClient.ExecCreateParam.attachStdout(),
                            DockerClient.ExecCreateParam.attachStderr())
                    def stream = docker.execStart(exe.id())
                    String shellResult = stream.readFully()?.trim()
                    if (LiveCheckConf.SHELL_RESULT_OK != shellResult) {
                        addEvent Event.builder().type(Event.Type.node).reason('live check fail').result('' + appId).
                                build().log(cmdLine)
                        return false
                    }
                }
            } else if (conf.isPortListen) {
                def publicPort = getPublicPort(conf.port, container)
                addEvent Event.builder().type(Event.Type.node).reason('live check').result('' + appId).
                        build().log('port listen ' + publicPort)
                if (Utils.isPortListenAvailable(publicPort)) {
                    addEvent Event.builder().type(Event.Type.node).reason('live check fail').result('' + appId).
                            build().log('port listen ' + publicPort)
                    return false
                }
            } else if (conf.isHttpRequest) {
                def publicPort = getPublicPort(conf.port, container)
                // eg. /health
                def uri = conf.httpRequestUri
                String url = 'http://127.0.0.1:' + publicPort + uri
                addEvent Event.builder().type(Event.Type.node).reason('live check').result('' + appId).
                        build().log('http check ' + url)

                def req = HttpRequest.get(url)
                def timeout = conf.httpTimeoutSeconds * 1000
                def code = req.connectTimeout(timeout).readTimeout(timeout).code()
                if (code != 200) {
                    addEvent Event.builder().type(Event.Type.node).reason('live check fail').result('' + appId).
                            build().log('http check ' + url + req.body())
                    return false
                }
            } else {
                return true
            }
        } catch (Exception e) {
            log.error('live check error - ' + appId, e)
            return false
        }
    }

    @Override
    String name() {
        'dms agent'
    }

    @Override
    void doJob() {
        sendNode()
        def containers = sendContainer()
        liveCheckAndCollectMetric(containers)
    }

    @Override
    void stop() {
        super.stop()
        collector.stop()
        if (docker) {
            docker.close()
            log.info 'done close docker client'
        }
        if (sigar) {
            sigar.close()
            log.info 'done close sigar client'
        }
    }
}
