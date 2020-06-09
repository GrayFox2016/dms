package server.scheduler

import common.Event
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.AppDTO
import model.AppJobDTO
import model.ClusterDTO
import server.InMemoryAllContainerManager
import spi.SpiSupport

@CompileStatic
@Slf4j
class RunAppTask implements Runnable {
    private int appId

    RunAppTask(int appId) {
        this.appId = appId
    }

    @Override
    void run() {
        Event.builder().type(Event.Type.cluster).reason('cron job scheduler task run').
                result('' + appId).build().log().toDto().add()

        def app = new AppDTO(id: appId).one() as AppDTO
        if (!app.jobConf || !app.jobConf.isOn) {
            log.info 'job is not on - ' + appId
            return
        }

        def lock = SpiSupport.createLock()
        lock.lockKey = 'guard ' + appId
        lock.exe {
            def cluster = new ClusterDTO(id: app.clusterId).one() as ClusterDTO
            def containerList = InMemoryAllContainerManager.instance.getContainerList(app.clusterId, appId)
            boolean isOk = Guardian.instance.guard(cluster, app, containerList)
            if (!isOk) {
                def job = new AppJobDTO(appId: appId).orderBy('created_date desc').one() as AppJobDTO
                if (job && job.status == AppJobDTO.Status.created.val) {
                    Guardian.instance.process(job, containerList, false)
                }
            }
        }
    }
}
