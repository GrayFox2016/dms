package ctrl

import auth.User
import org.segment.web.handler.ChainHandler
import server.scheduler.Guardian

def h = ChainHandler.instance

h.get('/manage/guard/pause') { req, resp ->
    User user = req.session('user')
    if (!user.isAdmin()) {
        resp.halt(403, 'not admin')
    }

    Guardian.instance.isRunning = false
    [flag: true]
}.get('/manage/guard/continue') { req, resp ->
    User user = req.session('user')
    if (!user.isAdmin()) {
        resp.halt(403, 'not admin')
    }

    Guardian.instance.isRunning = true
    [flag: true]
}