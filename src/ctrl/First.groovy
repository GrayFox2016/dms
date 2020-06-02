import org.apache.commons.lang.exception.ExceptionUtils
import org.segment.web.handler.ChainHandler
import org.slf4j.LoggerFactory

def h = ChainHandler.instance

def log = LoggerFactory.getLogger(this.class)

h.exceptionHandler { req, resp, t ->
    log.error('', t)
    resp.status = 500
    resp.outputStream << ExceptionUtils.getFullStackTrace(t)
}.get('/route/list') { req, resp ->
    [list: h.list.collect { it.name() }]
}