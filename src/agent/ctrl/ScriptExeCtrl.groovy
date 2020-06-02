package agent.ctrl

import agent.Agent
import agent.script.ScriptHolder
import org.segment.web.common.CachedGroovyClassLoader
import org.segment.web.handler.ChainHandler

def h = ChainHandler.instance

h.options('/script/exe') { req, resp ->
    def authToken = req.header(Agent.AUTH_TOKEN_HEADER) ?: req.param('authToken')
    assert authToken && authToken == Agent.instance.authToken

    Map params
    if (req.method() == 'GET') {
        params = [:]
        req.raw().getParameterNames().each {
            params[it] = req.param(it)
        }
    } else {
        params = req.bodyAs()
    }

    def scriptName = params.scriptName
    assert scriptName

    String scriptContent = ScriptHolder.instance.getContentByName(scriptName)
    assert scriptContent
    CachedGroovyClassLoader.instance.eval(scriptContent,
            [sigar: Agent.instance.sigar, docker: Agent.instance.docker, params: params])
}
