package agent

import common.Event
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.json.MonitorConf
import org.segment.d.NamedThreadFactory

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

@CompileStatic
@Singleton
@Slf4j
class ContainerStatCollector {

    private ExecutorService pool = new ThreadPoolExecutor(Math.min(2, Runtime.getRuntime().availableProcessors()),
            Math.min(2, Runtime.getRuntime().availableProcessors()) * 2,
            1000 * 10, java.util.concurrent.TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new NamedThreadFactory('container_stat_collector'));

    void collect(String containerId, Integer appId, Integer cpuPercent) {
        log.info 'begin collect - ' + appId + ' - cpu percent: ' + cpuPercent
        pool.submit {
            try {
                def agent = Agent.instance
                def docker = agent.docker
                def stats = docker.stats(containerId)
                def cpuStats = stats.cpuStats()
                def cpuDelta = cpuStats.cpuUsage().totalUsage() - stats.precpuStats().cpuUsage().totalUsage()
                def systemDelta = cpuStats.systemCpuUsage() - stats.precpuStats().systemCpuUsage()
                def percent = (cpuDelta > 0 && systemDelta > 0) ?
                        (cpuDelta / systemDelta * cpuStats.cpuUsage().percpuUsage().size() * 100) : 0
                Event.builder().type(Event.Type.node).reason('cpu collect').result('' + appId).build().log('' + percent)

                if (percent > cpuPercent) {
                    Map<String, Object> p = [:]
                    p.nodeIp = agent.nodeIp
                    p.appId = appId
                    p.scaleCmd = MonitorConf.SCALE_OUT
                    agent.get('/dms/api/app/scale', p)
                }
            } catch (Exception e) {
                log.error('container stats collect error - ' + appId + ' - ' + containerId, e)
            }
        }
    }

    void stop() {
        pool.shutdown()
        log.info 'stop container stats collect'
    }
}
