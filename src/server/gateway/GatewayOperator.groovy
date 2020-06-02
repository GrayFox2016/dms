package server.gateway

import com.github.kevinsawicki.http.HttpRequest
import common.Event
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.GwClusterDTO
import model.GwFrontendDTO
import model.json.GatewayConf
import model.json.GwBackendServer
import model.json.GwFrontendRuleConf
import model.json.KVPair
import server.scheduler.ContainerRunResult

@CompileStatic
@Slf4j
class GatewayOperator {
    private GatewayConf conf

    GatewayOperator(GatewayConf conf) {
        this.conf = conf
    }

    static final int DEFAULT_WEIGHT = 10

    static String scheme(String nodeIp, int publicPort) {
        "http://${nodeIp}:${publicPort}"
    }

    List<String> getBackendServerUrlList() {
        def frontend = new GwFrontendDTO(id: conf.frontendId).one() as GwFrontendDTO
        frontend.backend.serverList.collect {
            it.url
        }
    }

    private boolean changeBackend(String serverUrl, boolean isAdd = true, int weight = DEFAULT_WEIGHT) {
        def frontend = new GwFrontendDTO(id: conf.frontendId).one() as GwFrontendDTO
        def backend = frontend.backend

        boolean needUpdate
        if (isAdd) {
            def one = backend.serverList.find { it.url == serverUrl }
            if (one) {
                if (one.weight != weight) {
                    one.weight = weight
                    needUpdate = true
                }
            } else {
                backend.serverList.add(new GwBackendServer(url: serverUrl, weight: weight))
                needUpdate = true
            }
        } else {
            def one = backend.serverList.find { it.url == serverUrl }
            if (one) {
                backend.serverList.remove(one)
                needUpdate = true
            }
        }

        if (needUpdate) {
            Event.builder().type(Event.Type.cluster).reason('gateway backend ' + (isAdd ? 'add' : 'remove')).
                    result('' + frontend.name).build().log(backend.serverList.toString()).toDto().add()

            frontend.update()
            updateFrontend(frontend)
        }
    }

    private static void addKVPair(List<KVPair<String>> list, String path, Object value) {
        list << new KVPair<String>(key: path, value: value.toString())
    }

    static boolean updateFrontend(GwFrontendDTO frontend) {
        def one = new GwClusterDTO(id: frontend.clusterId).one() as GwClusterDTO
        def zk = ZkClientHolder.instance.create(one.zkNode)

        String rootPrefix
        String prefix
        if (!one.prefix.startsWith('/')) {
            rootPrefix = '/' + one.prefix
            prefix = '/' + rootPrefix + '/frontends/frontend' + frontend.id
        } else {
            rootPrefix = one.prefix
            prefix = rootPrefix + '/frontends/frontend' + frontend.id
        }

        List<KVPair<String>> list = []
        addKVPair(list, prefix + '/backend', 'backend' + frontend.id)
        addKVPair(list, prefix + '/priority', frontend.priority)
        addKVPair(list, prefix + '/passhostheader', frontend.conf.passHostHeader)

        frontend.conf.ruleConfList.eachWithIndex { GwFrontendRuleConf it, int i ->
            addKVPair(list, prefix + "/routes/rule_${i}/rule", "${it.type}${it.rule}")
        }
        frontend.auth.basicList.eachWithIndex { KVPair it, int i ->
            def val = it.key + ':' + HttpRequest.Base64.encode("${it.value}")
            addKVPair(list, prefix + "/auth/basic/users/${i}", val)
        }

        // backend
        String prefixBackend = rootPrefix + '/backends/backend' + frontend.id

        def backend = frontend.backend
        addKVPair(list, prefixBackend + "/maxconn/amount", backend.maxConn)
        addKVPair(list, prefixBackend + "/loadbalancer/method", backend.loadBalancer)
        if (backend.circuitBreaker) {
            addKVPair(list, prefixBackend + "/circuitbreaker/expression", backend.circuitBreaker)
        }
        if (backend.stickiness) {
            addKVPair(list, prefixBackend + "/loadbalancer/sticky", backend.stickiness)
        }
        if (backend.healthCheckUri) {
            addKVPair(list, prefixBackend + "/healthcheck/path", backend.stickiness)
        }
        backend.serverList.eachWithIndex { GwBackendServer it, int i ->
            addKVPair(list, prefixBackend + "/servers/server${i}/url", it.url)
            addKVPair(list, prefixBackend + "/servers/server${i}/weight", it.weight)
        }

        // remove frontend/backend
        zk.deleteRecursive(prefix)
        zk.deleteRecursive(prefixBackend)

        list.each {
            def key = it.key
            def value = it.value
            if (!zk.exists(key)) {
                zk.createPersistent(key, true)
                zk.writeData(key, value.bytes)
                log.info 'write - ' + key + ':' + value
            } else {
                def data = zk.readData(key)
                if (data && new String(data) == value.toString()) {
                    log.info 'skip - ' + key + ':' + value
                } else {
                    zk.writeData(key, value.bytes)
                    log.info 'write - ' + key + ':' + value
                }
            }
        }
        // trigger reload
        zk.deleteRecursive(rootPrefix + '/leader')
        true
    }

    boolean addBackend(ContainerRunResult result, int weight) {
        addBackend(scheme(result.nodeIp, result.port), true, weight)
    }

    boolean addBackend(String nodeIp, int port, int weight) {
        addBackend(scheme(nodeIp, port), true, weight)
    }

    boolean addBackend(String serverUrl, boolean waitDelayFirst = true, int weight = DEFAULT_WEIGHT) {
        if (!waitUntilHealthCheckOk(serverUrl, waitDelayFirst)) {
            return false
        }
        changeBackend(serverUrl, true, weight)
    }

    boolean removeBackend(String nodeIp, Integer port) {
        changeBackend(scheme(nodeIp, port), false)
    }

    boolean removeBackend(String serverUrl) {
        changeBackend(serverUrl, false)
    }

    boolean isBackendReady(String nodeIp, int port) {
        waitUntilHealthCheckOk(scheme(nodeIp, port), false)
    }

    private boolean waitUntilHealthCheckOk(String serverUrl, boolean waitDelayFirst) {
        if (!conf.healthCheckUri) {
            return true
        }

        String url = serverUrl + conf.healthCheckUri
        log.info 'begin health check - ' + url

        try {
            if (waitDelayFirst) {
                Thread.sleep((conf.healthCheckDelaySeconds ?: 10) * 1000)
            }

            def timeout = conf.healthCheckTimeoutSeconds ?: 3
            for (i in (0..<(conf.healthCheckTotalTimes ?: 3))) {
                def req = HttpRequest.get(url).connectTimeout(timeout * 1000).readTimeout(timeout * 1000)
                def code = req.code()
                if (200 == code) {
                    log.info 'health check ready for ' + url
                    return true
                }
                log.warn 'health check fail for ' + url + ' - code - ' + code + ' - ' + req.body()
                Thread.sleep((conf.healthCheckIntervalSeconds ?: 10) * 1000)
            }
            return false
        } catch (Exception e) {
            log.error('health check error for ' + url, e)
            return false
        }
    }
}
