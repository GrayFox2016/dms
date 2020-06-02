package script

import agent.Agent
import com.spotify.docker.client.DockerClient

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String id = params.id
if (!id) {
    return [error: 'id required']
}
docker.removeContainer(id, DockerClient.RemoveContainerParam.removeVolumes('1' == params.removeVolumes),
        DockerClient.RemoveContainerParam.forceKill('1' == params.forceKill))

Agent.instance.sendContainer()
[flag: true]
