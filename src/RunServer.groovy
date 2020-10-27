import common.Conf
import common.Const
import common.Utils
import conf.DefaultLocalH2DataSourceCreator
import org.segment.d.D
import org.segment.d.MySQLDialect
import org.segment.web.RouteRefreshLoader
import org.segment.web.RouteServer
import org.segment.web.common.CachedGroovyClassLoader
import org.segment.web.handler.ChainHandler
import org.slf4j.LoggerFactory
import server.InMemoryAllContainerManager
//import server.dns.EtcdClientHolder
import server.gateway.ZkClientHolder
import server.scheduler.Guardian

import java.sql.Types

def log = LoggerFactory.getLogger(this.getClass())

// project work directory set
def c = Conf.instance.resetWorkDir().loadArgs(args)
if (Conf.isWindows()) {
    c.put('dbDataDir', 'D:/var/dms/data')
    c.put('minAgentHeartBeatNodeIpSize', '3')
}
log.info c.toString()
def srcDirPath = c.projectPath('/src')
def resourceDirPath = c.projectPath('/resources')

// groovy class loader init
def loader = CachedGroovyClassLoader.instance
loader.init(Guardian.instance.class.classLoader, srcDirPath + ':' + resourceDirPath)

// chain filter uri prefix set
ChainHandler.instance.uriPre('/dms')

// DB
def ds = new DefaultLocalH2DataSourceCreator().create().cacheAs()
def d = new D(ds, new MySQLDialect())
// check if need create table first
def tableNameList = d.query('show tables', String).collect { it.toUpperCase() }
if (!tableNameList.contains('CLUSTER')) {
    new File(c.projectPath('/init_h2.sql')).text.split(';').each {
        try {
            d.exe(it)
        } catch (Exception e) {
            log.error('create table fail', e)
        }
    }
}
D.classTypeBySqlType[Types.TINYINT] = Integer
D.classTypeBySqlType[Types.SMALLINT] = Integer

// agent send container or node info to this manager
def manager = InMemoryAllContainerManager.instance
manager.start()

def guardian = Guardian.instance
guardian.interval = c.getInt('guardian.intervalSeconds', 10)
guardian.start()

// create jetty server, load route define interval us{
// ing cached groovy class loader
def server = RouteServer.instance
server.loader = RouteRefreshLoader.create(loader.gcl).addClasspath(srcDirPath).addClasspath(resourceDirPath).
        addDir(c.projectPath('/src/ctrl'))
server.webRoot = c.projectPath('/www')
server.start(Const.SERVER_HTTP_LISTEN_PORT)

Utils.stopWhenConsoleQuit {
    server.stop()
    guardian.stop()
    manager.stop()
    ds.closeConnect()
    ZkClientHolder.instance.close()
//    EtcdClientHolder.instance.close()
}