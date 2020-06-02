package script

import com.spotify.docker.client.DockerClient
import org.slf4j.LoggerFactory

DockerClient docker = super.binding.getProperty('docker')
Map params = super.binding.getProperty('params')
String id = params.id
String initCmd = params.initCmd
if (!id || !initCmd) {
    return [error: 'id/initCmd required']
}

def log = LoggerFactory.getLogger('dyn')

List<String> list = []
for (cmdLine in initCmd.readLines().findAll { it.trim() }) {
    log.info 'ready to exec cmd - ' + cmdLine
    String[] cmd = ['sh', '-c', cmdLine]
    def exe = docker.execCreate(id, cmd, DockerClient.ExecCreateParam.attachStdout(),
            DockerClient.ExecCreateParam.attachStderr())
    def warnings = exe.warnings()
    def stream = docker.execStart(exe.id())
    String shellResult = stream.readFully()?.trim()

    list << ('' + shellResult + (warnings ? warnings.toString() : ''))
}

[flag: true, message: list.join("\r\n")]