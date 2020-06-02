package script

import common.Conf
import org.hyperic.sigar.DirUsage
import org.hyperic.sigar.Sigar

Sigar sigar = super.binding.getProperty('sigar')
Map params = super.binding.getProperty('params')

String dirs = params.dirs
if (dirs) {
    Map<String, DirUsage> x = [:]
    dirs.split(',').each {
        String fileLocal = Conf.isWindows() ? it.replace('/c/Users/', 'C:/Users/') : it
        x[it] = sigar.getDirUsage(fileLocal)
    }
    return x
}

def r = [:]
sigar.fileSystemList.each {
    r[it.dirName] = sigar.getFileSystemUsage(it.dirName)
}
r
