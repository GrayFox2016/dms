package script.tpl

def instanceIndex = super.binding.getProperty('instanceIndex')
def nodeIpDockerHost = super.binding.getProperty('nodeIpDockerHost')
def containerNumber = super.binding.getProperty('containerNumber') as int
def nodeIpDockerHostList = super.binding.getProperty('nodeIpDockerHostList') as List<String>

def clusterName = super.binding.getProperty('clusterName')
def masterNumber = super.binding.getProperty('masterNumber') as int
def dataNumber = super.binding.getProperty('dataNumber') as int

int x = (masterNumber / 2 + 1) as int

"""
# ======================== Elasticsearch Configuration =========================
#
# NOTE: Elasticsearch comes with reasonable defaults for most settings.
#       Before you set out to tweak and tune the configuration, make sure you
#       understand what are you trying to accomplish and the consequences.
#
# The primary way of configuring a node is via this file. This template lists
# the most important settings you may want to configure for a production cluster.
#
# Please consult the documentation for further information on configuration options:
# https://www.elastic.co/guide/en/elasticsearch/reference/index.html
#
# ---------------------------------- Cluster -----------------------------------
#
# Use a descriptive name for your cluster:
#
cluster.name: ${clusterName}
#
# ------------------------------------ Node ------------------------------------
#
# Use a descriptive name for the node:
#
node.name: node-${instanceIndex}
node.master: ${instanceIndex < masterNumber ? 'true' : 'false'}
node.data: ${instanceIndex < (containerNumber - dataNumber) ? 'false' : 'true'}
#
# Add custom attributes to the node:
#
#node.attr.rack: r1
#
# ----------------------------------- Paths ------------------------------------
#
# Path to directory where to store the data (separate multiple locations by comma):
#
#path.data: /path/to/data
#
# Path to log files:
#
#path.logs: /path/to/logs
#
# ----------------------------------- Memory -----------------------------------
#
# Lock the memory on startup:
#
#bootstrap.memory_lock: true
#
# Make sure that the heap size is set to about half the memory available
# on the system and that the owner of the process is allowed to use this
# limit.
#
# Elasticsearch performs poorly when the system is swapping the memory.
#
# ---------------------------------- Network -----------------------------------
#
# Set the bind address to a specific IP (IPv4 or IPv6):
#
network.host: 0.0.0.0
network.publish_host: ${nodeIpDockerHost}
#
# Set a custom port for HTTP:
#
http.port: ${9200 + instanceIndex}
transport.tcp.port: ${9300 + instanceIndex}
#
# For more information, consult the network module documentation.
#
# --------------------------------- Discovery ----------------------------------
#
# Pass an initial list of hosts to perform discovery when new node is started:
# The default list of hosts is ["127.0.0.1", "[::1]"]
#
discovery.zen.ping.unicast.hosts: [${nodeIpDockerHostList[0..<masterNumber].collect { '"' + it + '"' }.join(',')}]
#
# Prevent the "split brain" by configuring the majority of nodes (total number of master-eligible nodes / 2 + 1):
#
discovery.zen.minimum_master_nodes: ${x}
#
# For more information, consult the zen discovery module documentation.
#
# ---------------------------------- Gateway -----------------------------------
#
# Block initial recovery after a full cluster restart until N nodes are started:
#
#gateway.recover_after_nodes: 3
#
# For more information, consult the gateway module documentation.
#
# ---------------------------------- Various -----------------------------------
#
# Require explicit names when deleting indices:
#
#action.destructive_requires_name: true
"""