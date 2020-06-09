package script.tpl

def appId = super.binding.getProperty('appId') as int
def instanceIndex = super.binding.getProperty('instanceIndex') as int
def nodeIpDockerHost = super.binding.getProperty('nodeIpDockerHost')
def nodeIpDockerHostList = super.binding.getProperty('nodeIpDockerHostList') as List<String>
def list = []
nodeIpDockerHostList.eachWithIndex { String nodeIp, int i ->
    list << "etcd${i}=http://${nodeIp}:${2380 + i}"
}
def x = list.join(',')

"""
name: etcd${instanceIndex}
data-dir: /opt/etcd/data
listen-client-urls: http://${nodeIpDockerHost}:${2379 - instanceIndex},http://127.0.0.1:${2379 - instanceIndex}
advertise-client-urls: http://${nodeIpDockerHost}:${2379 - instanceIndex},http://127.0.0.1:${2379 - instanceIndex}

listen-peer-urls: http://${nodeIpDockerHost}:${2380 + instanceIndex}
initial-advertise-peer-urls: http://${nodeIpDockerHost}:${2380 + instanceIndex}
initial-cluster: ${x}
initial-cluster-state: new
initial-cluster-token: cluster-${appId}
"""
