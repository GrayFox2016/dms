package script

import com.spotify.docker.client.DockerClient

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String id = params.id
if (!id) {
    return [error: 'id required']
}
[container: docker.inspectContainer(id)]
