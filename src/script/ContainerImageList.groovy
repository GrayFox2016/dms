package script

import com.spotify.docker.client.DockerClient

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String keyword = params.keyword
if (!keyword) {
    return [error: 'keyword required']
}
[list: docker.listImages(DockerClient.ListImagesParam.byName(keyword))]
