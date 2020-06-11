package script.tpl

import server.scheduler.ContainerMountTplHelper

def productName = super.binding.getProperty('productName')

def zkAppName = super.binding.getProperty('zkAppName')
ContainerMountTplHelper applications = super.binding.getProperty('applications')
def zkApp = applications.app(zkAppName)
//def zookeeperConnect = zkApp.getAllNodeIpList().collect { it + ':2181' }.join(',')
def firstNodeIp = zkApp.getAllNodeIpList()[0]
def zookeeperConnect = [2181, 2182, 2183].collect { firstNodeIp + ':' + it }.join(',')

"""
coordinator_name = "zookeeper"
coordinator_addr = "${zookeeperConnect}"
#coordinator_auth = ""

# Set Codis Product Name/Auth.
product_name = "${productName}"
product_auth = ""

# Set bind address for admin(rpc), tcp only.
admin_addr = "0.0.0.0:18080"

# Set arguments for data migration (only accept 'sync' & 'semi-async').
migration_method = "semi-async"
migration_parallel_slots = 100
migration_async_maxbulks = 200
migration_async_maxbytes = "32mb"
migration_async_numkeys = 500
migration_timeout = "30s"

# Set configs for redis sentinel.
sentinel_client_timeout = "10s"
sentinel_quorum = 2
sentinel_parallel_syncs = 1
sentinel_down_after = "30s"
sentinel_failover_timeout = "5m"
sentinel_notification_script = ""
sentinel_client_reconfig_script = ""
"""