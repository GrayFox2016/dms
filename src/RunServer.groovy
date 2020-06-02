import common.Conf
import common.Utils
import conf.DataSourceCreator
import org.segment.d.D
import org.segment.d.MySQLDialect
import org.segment.web.RouteRefreshLoader
import org.segment.web.RouteServer
import org.segment.web.common.CachedGroovyClassLoader
import org.segment.web.handler.ChainHandler
import org.slf4j.LoggerFactory
import server.InMemoryAllContainerManager
import server.gateway.ZkClientHolder
import server.scheduler.Guardian
import spi.SpiSupport

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
loader.init(this.getClass().classLoader, srcDirPath + ':' + resourceDirPath)

// chain filter uri prefix set
ChainHandler.instance.uriPre('/dms')

// DB
DataSourceCreator creator = SpiSupport.createDataSourceCreator()
def ds = creator.create().cacheAs()
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
guardian.start()

// create jetty server, load route define interval us{
// ing cached groovy class loader
def server = RouteServer.instance
server.loader = RouteRefreshLoader.create(loader.gcl).addClasspath(srcDirPath).addClasspath(resourceDirPath).
        addDir(c.projectPath('/src/ctrl'))
server.start()

Utils.stopWhenConsoleQuit {
    server.stop()
    guardian.stop()
    manager.stop()
    ds.closeConnect()
    ZkClientHolder.instance.close()
}