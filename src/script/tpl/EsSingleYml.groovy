package script.tpl

def nodeIpDockerHost = super.binding.getProperty('nodeIpDockerHost')

"""
network.host: ${nodeIpDockerHost}
network.publish_host: ${nodeIpDockerHost}
"""