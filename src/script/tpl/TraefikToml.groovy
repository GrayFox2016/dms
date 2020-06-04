package script.tpl

def logLevel = super.binding.getProperty('logLevel')
def zkConnectString = super.binding.getProperty('zkConnectString')
def serverPort = super.binding.getProperty('serverPort')
def dashboardPort = super.binding.getProperty('dashboardPort')
def prefix = super.binding.getProperty('prefix')

"""
logLevel="${logLevel}"
defaultEntryPoints=["http"]
[entryPoints]
    [entryPoints.http]
    address=":${serverPort}"
    [entryPoints.bar]
    address=":${dashboardPort}"
[traefikLog]
filePath="/var/log/traefik/log.txt"
[accessLog]
filePath="/var/log/traefik/access.log"
[api]
entryPoint="bar"
dashboard=true
[zookeeper]
endpoint="${zkConnectString}"
watch=true
prefix="${prefix}"
"""
