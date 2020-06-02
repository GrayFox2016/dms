package server

import agent.Agent
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.github.kevinsawicki.http.HttpRequest
import common.Const
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang.exception.ExceptionUtils
import org.segment.d.json.JsonWriter

@CompileStatic
@Singleton
@Slf4j
class AgentCaller {

    int connectTimeout = 500

    int readTimeout = 2000

    public <T> T get(String nodeIp, String uri, Map params = null, Class<T> clz = String,
                     Closure<Void> failCallback = null) {
        String authToken = InMemoryAllContainerManager.instance.getAuthToken(nodeIp)
        assert authToken

        String serverUrl = 'http://' + nodeIp + ':' + Const.AGENT_HTTP_LISTEN_PORT
        def req = HttpRequest.get(serverUrl + uri, params ?: [:], true).
                connectTimeout(connectTimeout).readTimeout(readTimeout).
                header(Agent.AUTH_TOKEN_HEADER, authToken)
        def body = req.body()
        if (req.code() != 200) {
            if (failCallback) {
                log.warn('server get agent info fail - ' + uri + ' - ' + params + ' - ' + body)
                failCallback.call(body)
            } else {
                throw new RuntimeException('server get agent info fail - ' + uri + ' - ' + params + ' - ' + body)
            }
        }
        if (clz == String) {
            return body as T
        }
        JSON.parseObject(body, clz)
    }

    public <T> T post(String nodeIp, String uri, Map params = null, Class<T> clz = String,
                      Closure failCallback = null) {
        String authToken = InMemoryAllContainerManager.instance.getAuthToken(nodeIp)
        assert authToken

        int readTimeoutLocal
        if (params?.readTimeout) {
            readTimeoutLocal = params.readTimeout as int
        } else {
            readTimeoutLocal = readTimeout
        }

        String serverUrl = 'http://' + nodeIp + ':' + Const.AGENT_HTTP_LISTEN_PORT
        try {
            def req = HttpRequest.post(serverUrl + uri).
                    connectTimeout(connectTimeout).readTimeout(readTimeoutLocal).
                    header(Agent.AUTH_TOKEN_HEADER, authToken)
            def sendBody = JsonWriter.instance.json(params ?: [:])
            def body = req.send(sendBody).body()
            if (req.code() != 200) {
                if (failCallback) {
                    log.warn('server post agent info fail - ' + uri + ' - ' + sendBody + ' - ' + body)
                    failCallback.call(body)
                } else {
                    throw new RuntimeException('server post agent info fail - ' + uri + ' - ' + sendBody + ' - ' + body)
                }
            }
            if (clz == String) {
                return body as T
            }
            JSON.parseObject(body, clz)
        } catch (Exception e) {
            if (failCallback) {
                failCallback.call(ExceptionUtils.getFullStackTrace(e))
            } else {
                throw e
            }
        }
    }

    JSONObject agentScriptExe(String nodeIp, String scriptName, Map params = null, Closure failCallback = null) {
        Map p = params ?: [:]
        p.scriptName = scriptName
        String body = post(nodeIp, '/dmc/script/exe', p, String, failCallback)
        JSONObject.parseObject(body)
    }
}
