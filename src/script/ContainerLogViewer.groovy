package script

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.LogsParam

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String id = params.id
if (!id) {
    return [error: 'id required']
}

int tailLines = params.tailLines ? params.tailLines as int : 100
int oneDayAgo = System.currentTimeMillis() / 1000 - 24 * 3600
int since = params.since ? params.since as int : oneDayAgo

def stream = docker.logs(id, LogsParam.timestamps(), LogsParam.since(since),
        LogsParam.stdout(), LogsParam.stderr(), LogsParam.tail(tailLines))

stream.readFully()
