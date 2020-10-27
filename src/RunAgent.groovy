import agent.Agent
import agent.script.ScriptHolder
import com.spotify.docker.client.DefaultDockerClient
import common.Conf
import common.Const
import common.Utils
import org.hyperic.sigar.Sigar
import org.segment.web.RouteRefreshLoader
import org.segment.web.RouteServer
import org.segment.web.common.CachedGroovyClassLoader
import org.segment.web.handler.ChainHandler
import org.slf4j.LoggerFactory

def log = LoggerFactory.getLogger(this.getClass())

// project work directory set
def c = Conf.instance.resetWorkDir().loadArgs(args)
log.info c.toString()
def srcDirPath = c.projectPath('/src')

// agent
def agent = Agent.instance
agent.serverUrl = c.getString('serverUrl', 'http://localhost:' + Const.SERVER_HTTP_LISTEN_PORT)
agent.clusterId = c.getInt('clusterId', 1)
agent.secret = c.getString('secret', '1')
agent.auth()
agent.scriptAgentCollectSet << 'node base info'
agent.sigar = new Sigar()
agent.nodeIp = Utils.localIp()
agent.nodeIpDockerHost = c.getString('nodeIpDockerHost', agent.nodeIp)
agent.interval = c.getInt('agent.intervalSeconds', 10)

def dockerHostUri = c.getString('dockerHostUri', 'https://' + agent.nodeIpDockerHost + ':2376')
agent.docker = DefaultDockerClient.fromEnv().uri(dockerHostUri).build()
agent.start()

// script holder
def scriptHolder = ScriptHolder.instance
scriptHolder.agent = agent
scriptHolder.start()

// groovy class loader init
def loader = CachedGroovyClassLoader.instance
loader.init(agent.class.classLoader, srcDirPath)

// chain filter uri prefix set
ChainHandler.instance.uriPre('/dmc')

// create jetty server, load route define interval using cached groovy class loader
def server = RouteServer.instance
server.loader = RouteRefreshLoader.create(loader.gcl).addClasspath(srcDirPath).
        addDir(c.projectPath('/src/agent/ctrl'))
server.start(Const.AGENT_HTTP_LISTEN_PORT)

Utils.stopWhenConsoleQuit {
    scriptHolder.stop()
    agent.stop()
    server.stop()
}