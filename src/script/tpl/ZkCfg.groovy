package script.tpl

def instanceIndex = super.binding.getProperty('instanceIndex')
def nodeIpList = super.binding.getProperty('nodeIpList') as List<String>
def list = []
nodeIpList.eachWithIndex { String nodeIp, int i ->
    list << "server.${i}=127.0.0.1:${2287 + i}:${3387 + i}"
}
"""
tickTime=2000
initLimit=5
syncLimit=2
dataDir=/opt/zk/data
clientPort=${2181 + instanceIndex}
metricsProvider.className=org.apache.zookeeper.metrics.prometheus.PrometheusMetricsProvider
metricsProvider.httpPort=${7000 + instanceIndex}
metricsProvider.exportJvmInfo=true

admin.enableServer=false

${list.join("\r\n")}
"""