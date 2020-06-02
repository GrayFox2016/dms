package script

import agent.Agent
import com.spotify.docker.client.DockerClient

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String id = params.id
if (!id) {
    return [error: 'id required']
}

int secondsToWaitBeforeKilling = params.secondsToWaitBeforeKilling ?
        params.secondsToWaitBeforeKilling as int : 10
docker.stopContainer(id, Math.max(secondsToWaitBeforeKilling, 20))

if (params.isRemoveAfterStop) {
    docker.removeContainer(id)
}

Agent.instance.sendContainer()
[flag: true]
