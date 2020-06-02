package script

import agent.AgentTempInfoHolder
import common.Conf
import org.hyperic.sigar.FileSystem
import org.hyperic.sigar.Sigar

// script name
final String scriptName = 'node base info'

Sigar sigar = super.binding.getProperty('sigar')
def r = [:]
r.cpuPercList = sigar.cpuPercList.collect {
    [user: it.user, sys: it.sys, idle: it.idle]
}
def mem = sigar.mem
r.mem = [total: mem.total / 1024 / 1024, free: mem.free / 1024 / 1024, usedPercent: mem.usedPercent]
if (!Conf.isWindows()) {
    r.loadAverage = sigar.loadAverage
}
r.netStat = sigar.netStat
//r.netInterfaceStatList = sigar.netInterfaceList.collect {
//    def stat = sigar.getNetInterfaceStat(it)
//    [name: it, rxBytes: stat.rxBytes, txBytes: stat.txBytes, speed: stat.speed]
//}
r.fsUsageList = sigar.fileSystemList.collect { FileSystem it ->
    def usage = sigar.getFileSystemUsage(it.dirName)
    [dirName   : it.dirName, total: (usage.total / 1024 / 1024).doubleValue().round(2),
     free      : (usage.free / 1024 / 1024).doubleValue().round(2),
     usePercent: usage.usePercent * 100]
}
AgentTempInfoHolder.instance.add(AgentTempInfoHolder.Type.node, r)
r
