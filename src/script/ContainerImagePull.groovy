package script

import agent.Agent
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.ProgressHandler
import com.spotify.docker.client.exceptions.DockerException
import com.spotify.docker.client.messages.ProgressMessage
import com.spotify.docker.client.messages.RegistryAuth
import common.Conf
import model.ImageRegistryDTO
import org.segment.d.json.JsonWriter
import org.slf4j.LoggerFactory

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String image = params.image
String registryId = params.registryId
if (!image) {
    return [error: 'image required']
}

ImageRegistryDTO hub = Agent.instance.get('/dms/api/image/pull/hub/info', [registryId: registryId], ImageRegistryDTO)
def auth = RegistryAuth.create(hub.anon() ? null : hub.loginUser, hub.anon() ? null : hub.loginPassword, '',
        hub.trimScheme(), null, null)

def log = LoggerFactory.getLogger('container image pull')
def r = [:]

def latch = new CountDownLatch(1)
docker.pull(image, auth, new ProgressHandler() {
    @Override
    void progress(ProgressMessage message) throws DockerException {
        log.info JsonWriter.instance.json(message)
        boolean isError = message.error()?.contains('Error')
        boolean isDownloaded = message.status()?.contains('Downloaded newer')
        r.isError = isError
        if (isError || isDownloaded) {
            r.message = message.status() ?: message.error()
            latch.countDown()
        }
    }
})

def timeout = Conf.instance.getInt('imagePullTimeoutSeconds', 30) as long
latch.await(timeout, TimeUnit.SECONDS)
r