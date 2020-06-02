package ctrl

import auth.User
import model.AppJobDTO
import model.AppJobLogDTO
import org.segment.web.handler.ChainHandler
import server.scheduler.JobStepKeeper

def h = ChainHandler.instance

h.group('/app/job') {
    h.get('/list') { req, resp ->
        def appId = req.param('appId')
        assert appId
        User user = req.session('user')
        if (!user.isAccessApp(appId as int)) {
            resp.halt(403, 'not this app manager')
        }

        new AppJobDTO(appId: appId as int).orderBy('created_date desc').loadList(100)
    }.get('/log/list') { req, resp ->
        def jobId = req.param('jobId')
        assert jobId
        def job = new AppJobDTO(id: jobId as int).one() as AppJobDTO
        assert job

        User user = req.session('user')
        if (!user.isAccessApp(job.appId)) {
            resp.halt(403, 'not this app manager')
        }

        def list = new AppJobLogDTO(jobId: jobId as int).orderBy('instance_index desc').
                loadList(100) as List<AppJobLogDTO>
        list.collect {
            it.prop('isDoneOk', JobStepKeeper.isJobLogDoneOk(it))
            it.rawProps(true)
        }
    }
}
