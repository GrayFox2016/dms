package script.tpl

import server.scheduler.ContainerMountTplHelper

def kafkaAppName = super.binding.getProperty('kafkaAppName')
def esAppName = super.binding.getProperty('esAppName')
ContainerMountTplHelper applications = super.binding.getProperty('applications')
def kafkaApp = applications.app(kafkaAppName)
def esApp = applications.app(esAppName)

def brokers = kafkaApp.allNodeIpList.collect { it + ':9092' }.join(',')
def esServers = esApp.allNodeIpList.collect { '"' + it + ':9200' + '"' }.join(',')

"""
input {
    kafka {
        type => "beat_app"
        bootstrap_servers => "${brokers}"
        topics => "beat_app"
        group_id => "dms_logstash"
        codec => json {
            charset => "utf-8"
        }
    }
}
output {
    if [type] == "beat_app" {
        elasticsearch {
            hosts => [${esServers}]
            index => "%{[fields][app_id]}"
        }
    }
}
"""