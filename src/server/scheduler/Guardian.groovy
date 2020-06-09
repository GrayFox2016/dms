package server.scheduler

import com.alibaba.fastjson.JSONObject
import common.Conf
import common.Event
import common.IntervalJob
import common.LimitQueue
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.*
import org.apache.commons.lang.exception.ExceptionUtils
import org.segment.d.D
import server.AgentCaller
import server.InMemoryAllContainerManager
import server.dns.DnsOperator
import server.dns.EtcdClientHolder
import server.gateway.GatewayOperator
import spi.SpiSupport

import static common.ContainerHelper.*

@CompileStatic
@Singleton
@Slf4j
class Guardian extends IntervalJob {
    @Override
    String name() {
        'scheduler guardian'
    }

    @Override
    void doJob() {
        int minAgentHeartBeatNodeIpSize = Conf.instance.getInt('minAgentHeartBeatNodeIpSize', 10)
        if (agentHeartBeatNodeIpList.size() < minAgentHeartBeatNodeIpSize) {
            log.warn 'wait more agent heart beat - ' + agentHeartBeatNodeIpList.size()
            return
        }

        if (!isRunning) {
            log.warn 'guard not running'
            return
        }

        clearOldEventLog()
        isCronJobRefreshDone = CronJobRunner.instance.refresh()

        failHealthCheckAgentNodeIpListCopy.clear()
        failGuardAppIdListCopy.clear()
        failAppJobIdListCopy.clear()

        Event.builder().type(Event.Type.cluster).reason('guard').
                result(new Date().format(D.ymdhms)).build().log().toDto().add()

        def clusterList = new ClusterDTO().where('1=1').loadList() as List<ClusterDTO>
        for (cluster in clusterList) {
            agentHealthCheck(cluster.id)

            def appList = new AppDTO(clusterId: cluster.id).loadList() as List<AppDTO>
            if (intervalCount % 10 == 0) {
                log.info 'begin check cluster app - ' + appList.collect { it.name } + ' for cluster - ' + cluster.name
            }

            def containerList = InMemoryAllContainerManager.instance.getContainerList(cluster.id)
            for (app in appList) {
                if (!app.autoManage()) {
                    log.debug 'skip guard app - ' + app.name
                    continue
                }

                if (app.jobConf && app.jobConf.cronExp) {
                    log.debug 'skip guard app as it is cron job - ' + app.name
                    continue
                }

                def thisAppContainerList = containerList.findAll { x ->
                    app.id == getAppId(x)
                }

                def lock = SpiSupport.createLock()
                lock.lockKey = 'guard ' + app.id
                boolean isDone = lock.exe {
                    checkApp(cluster, app, thisAppContainerList)
                }
                if (!isDone) {
                    log.info 'get app guard lock fail - ' + app.name
                }
            }
        }

        failHealthCheckAgentNodeIpList.clear()
        failHealthCheckAgentNodeIpList.addAll(failHealthCheckAgentNodeIpListCopy)
        failGuardAppIdList.clear()
        failGuardAppIdList.addAll(failGuardAppIdListCopy)
        failAppJobIdList.clear()
        failAppJobIdList.addAll(failAppJobIdListCopy)
    }

    private void clearOldEventLog() {
        def now = new Date()
        if (!(now.hours == 23 && now.minutes == 59 && now.seconds >= (60 - interval / 1000))) {
            if (!Conf.isWindows()) {
                return
            }
            if (intervalCount % 10 != 0) {
                return
            }
        }

        try {
            int dayAfter = Conf.instance.getInt('clearOldEventLogDayAfter', 7)
            def one = new EventDTO()
            def num = one.useD().exeUpdate('delete from ' + one.tbl() + ' where created_date < ?', [now - dayAfter])
            log.info 'done delete old event log - ' + num

            def one2 = new AppJobLogDTO()
            def num2 = one.useD().exeUpdate('delete from ' + one2.tbl() + ' where created_date < ?', [now - dayAfter])
            log.info 'done delete old job log - ' + num2
        } catch (Exception e) {
            log.error('clear old event log error', e)
        }
    }

    private void agentHealthCheck(int clusterId) {
        def instance = InMemoryAllContainerManager.instance
        def nodeList = instance.getHeartBeatOkNodeList(clusterId)
        if (!nodeList) {
            return
        }

        for (one in nodeList) {
            def nodeInfo = instance.getNodeInfo(one.ip)
            if (!nodeInfo.getBoolean('isLiveCheckOk') || !nodeInfo.getBoolean('isMetricGetOk')) {
                failHealthCheckAgentNodeIpListCopy << one.ip
                log.warn 'agent health check fail - ' + one.ip
            }
        }
    }

    @Override
    void stop() {
        super.stop()
        CronJobRunner.instance.stop()
    }

    private static Map<Integer, GuardianProcessor> processors = [:]

    static {
        processors[AppJobDTO.JobType.create.val] = new CreateProcessor()
        processors[AppJobDTO.JobType.remove.val] = new RemoveProcessor()
        processors[AppJobDTO.JobType.scroll.val] = new ScrollProcessor()
    }

    Set<Integer> failGuardAppIdList = []
    private Set<Integer> failGuardAppIdListCopy = []

    Set<Integer> failAppJobIdList = []
    private Set<Integer> failAppJobIdListCopy = []

    Set<String> failHealthCheckAgentNodeIpList = []
    private Set<String> failHealthCheckAgentNodeIpListCopy = []

    boolean isCronJobRefreshDone = true

    private LimitQueue<String> agentHeartBeatNodeIpList = new LimitQueue<>(100)

    synchronized void addAgentHeartBeatNodeIp(String nodeIp) {
        agentHeartBeatNodeIpList << nodeIp
    }

    volatile boolean isRunning = true

    private void stopNotRunning(Integer appId, List<JSONObject> containerList) {
        containerList.findAll { x ->
            !isRunning(x)
        }.each { x ->
            def id = getContainerId(x)
            def nodeIp = getNodeIp(x)

            def p = [id: id]
            if ('created' == x.getString('State')) {
                p.isRemoveAfterStop = '1'
                AgentCaller.instance.agentScriptExe(nodeIp, 'container stop', p) { String body ->
                    log.warn 'stop container fail - ' + body
                }
                log.info 'done stop and remove container - ' + id + ' - ' + appId
            } else if ('exited' == x.getString('State')) {
                AgentCaller.instance.agentScriptExe(nodeIp, 'container remove', p) { String body ->
                    log.warn 'remove container fail - ' + body
                }
                log.info 'done remove container - ' + id + ' - ' + appId
            }
        }
    }

    boolean guard(ClusterDTO cluster, AppDTO app, List<JSONObject> containerList) {
        try {
            stopNotRunning(app.id, containerList)
            def list = containerList.findAll { x ->
                isRunning(x)
            }

            // update dns
            if (cluster.globalEnvConf.dnsEndpoints && cluster.globalEnvConf.dnsKeyPrefix) {
                if (intervalCount % 10 == 0) {
                    def dnsTtl = Conf.instance.getInt('dnsTtl', 3600)
                    def client = EtcdClientHolder.instance.create(cluster.globalEnvConf.dnsEndpoints)
                    for (x in list) {
                        boolean isOk = new DnsOperator(client, cluster.globalEnvConf.dnsKeyPrefix).put(
                                generateContainerHostname(app.id, getAppInstanceIndex(x)),
                                getNodeIpDockerHost(x), dnsTtl)
                        log.info 'done update dns record - ' + app.name + ' - ' + isOk
                    }
                }
            }

            def containerNumber = app.conf.containerNumber
            if (containerNumber == list.size()) {
                def gatewayConf = app.gatewayConf
                if (!gatewayConf) {
                    return true
                }

                // check gateway
                def operator = GatewayOperator.create(app.id, gatewayConf)
                List<String> runningServerUrlList = list.collect { x ->
                    def nodeIpDockerHost = getNodeIpDockerHost(x)
                    def publicPort = getPublicPort(gatewayConf.containerPrivatePort, x)
                    GatewayOperator.scheme(nodeIpDockerHost, publicPort)
                }
                List<String> backendServerUrlList = operator.getBackendServerUrlListFromApi()
                (backendServerUrlList - runningServerUrlList).each {
                    operator.removeBackend(it)
                }
                (runningServerUrlList - backendServerUrlList).each {
                    operator.addBackend(it, false)
                }
                return true
            } else {
                log.warn 'app running not match ' + containerNumber + ' but - ' +
                        list.collect { x -> x.getString('State') } + ' for app - ' + app.name
                if (containerNumber < list.size()) {
                    new AppJobDTO(appId: app.id, failNum: 0, status: AppJobDTO.Status.created.val,
                            jobType: AppJobDTO.JobType.remove.val, createdDate: new Date(), updatedDate: new Date()).
                            addParam('toContainerNumber', containerNumber).add()
                } else if (containerNumber > list.size()) {
                    List<Integer> needRunInstanceIndexList = []
                    (0..<containerNumber).each { Integer i ->
                        def one = list.find { x ->
                            i == getAppInstanceIndex(x)
                        }
                        if (!one) {
                            needRunInstanceIndexList << i
                        }
                    }

                    new AppJobDTO(appId: app.id, failNum: 0, status: AppJobDTO.Status.created.val,
                            jobType: AppJobDTO.JobType.create.val, createdDate: new Date(), updatedDate: new Date()).
                            addParam('needRunInstanceIndexList', needRunInstanceIndexList).add()
                }
                Event.builder().type(Event.Type.app).reason('change container number').
                        result('' + app.id).build().log('from - ' + list.size() + ' -> ' + containerNumber).toDto().add()
                return false
            }
        } catch (Exception e) {
            log.error('guard app error - ' + app.name, e)
            return false
        }
    }

    boolean process(AppJobDTO job, List<JSONObject> containerList, boolean doStopNotRunning = true) {
        try {
            new AppJobDTO(id: job.id, status: AppJobDTO.Status.processing.val, updatedDate: new Date()).update()
            Event.builder().type(Event.Type.app).reason('process job').
                    result('' + job.appId).build().log('update job status to processing').toDto().add()

            if (doStopNotRunning) {
                stopNotRunning(job.appId, containerList)
            }

            def list = containerList.sort { x ->
                getAppId(x)
            }
            def app = new AppDTO(id: job.appId).one() as AppDTO
            if (!app) {
                log.warn 'no app found - ' + job.appId
                return false
            }

            def processor = Guardian.processors[job.jobType]
            processor.process(job, app, list)

            new AppJobDTO(id: job.id, status: AppJobDTO.Status.done.val, updatedDate: new Date()).update()
            Event.builder().type(Event.Type.app).reason('process job').
                    result('' + job.appId).build().log('update job status to done').toDto().add()
            return true
        } catch (Exception e) {
            log.error('process app job error - ' + job.appId, e)
            new AppJobDTO(id: job.id, status: AppJobDTO.Status.failed.val, message: ExceptionUtils.getFullStackTrace(e),
                    failNum: job.failNum + 1, updatedDate: new Date()).update()
            Event.builder().type(Event.Type.app).reason('process job').
                    result('' + job.appId).build().log('update job status to failed').toDto().add()
            return false
        }
    }

    void checkApp(ClusterDTO cluster, AppDTO app, List<JSONObject> containerList) {
        final int appJobBatchSize = Conf.instance.getInt('appJobBatchSize', 10)
        final int appJobMaxFailTimes = Conf.instance.getInt('appJobMaxFailTimes', 3)

        AppJobDTO job
        def jobList = new AppJobDTO(appId: app.id).orderBy('created_date desc').
                loadList(appJobBatchSize) as List<AppJobDTO>
        if (jobList) {
            def uniqueJobList = jobList.unique { it.status }
            def todoJob = uniqueJobList.find {
                it.status != AppJobDTO.Status.failed.val && it.status != AppJobDTO.Status.done.val
            }
            if (todoJob) {
                job = todoJob
            } else {
                def failJob = uniqueJobList.find { it.status == AppJobDTO.Status.failed.val }
                if (failJob) {
                    if (failJob.failNum != null && failJob.failNum >= appJobMaxFailTimes) {
                        // go on check
                    } else {
                        job = failJob
                    }
                }
            }
        }

        if (job) {
            def isDone = process(job, containerList)
            if (!isDone) {
                failAppJobIdListCopy << app.id
            }
        } else {
            boolean isOk = guard(cluster, app, containerList)
            if (!isOk) {
                failGuardAppIdListCopy << app.id
            } else {
                // clear old fail job
                int num = new AppJobDTO().where('app_id=?', app.id).
                        where('status=?', AppJobDTO.Status.failed.val).
                        where('fail_num>=?', appJobMaxFailTimes).deleteAll()
                if (num) {
                    Event.builder().type(Event.Type.app).reason('delete failed job').
                            result('' + app.id).build().log('delete num - ' + num).toDto().add()
                }
            }
        }
    }
}
