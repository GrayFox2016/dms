package ctrl

import agent.Agent
import model.ClusterDTO
import org.apache.commons.codec.digest.DigestUtils
import org.segment.web.handler.ChainHandler

def h = ChainHandler.instance
h.before('/api/**') { req, resp ->
    // check auth token
    def authToken = req.header(Agent.AUTH_TOKEN_HEADER)
    def clusterId = req.header(Agent.CLUSTER_ID_HEADER)
    if (!authToken || !clusterId) {
        resp.halt(403, 'require authToken && clusterId')
    }

    def one = new ClusterDTO(id: clusterId as int).queryFields('secret').one() as ClusterDTO
    if (DigestUtils.md5Hex(one.secret + req.host()) != authToken) {
        resp.halt(403, 'authToken not match')
    }
}

h.before('/**') { req, resp ->
    def uri = req.uri()
    // not api
    if (uri.contains('/api/')) {
        return
    }
    if (uri.endsWith('/login') || uri.endsWith('/logout') ||
            uri.endsWith('/agent/auth') || uri.endsWith('/hz')) {
        return
    }

    // check login
    def u = req.session('user')
    if (!u) {
        resp.halt(403, 'need login')
    }
}
