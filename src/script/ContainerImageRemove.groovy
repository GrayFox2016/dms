package script

import com.spotify.docker.client.DockerClient

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String image = params.image
if (!image) {
    return [error: 'image required']
}
[list: docker.removeImage(image)]