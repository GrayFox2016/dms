package script.tpl

import server.scheduler.ContainerMountTplHelper

def keyPrefix = super.binding.getProperty('keyPrefix')
def etcdAppName = super.binding.getProperty('etcdAppName')
ContainerMountTplHelper applications = super.binding.getProperty('applications')
def etcdApp = applications.app(etcdAppName)

def x = "http://${etcdApp.allNodeIpList[0]}:2379"

"""
.:53 {
    etcd {
        stubzones
        path ${keyPrefix}
        endpoint ${x}
        upstream 119.29.29.29 223.5.5.5 /etc/resolv.conf
    }
    log stdout
    errors stdout
    #proxy . 119.29.29.29 223.5.5.5 /etc/resolv.conf
}
"""
