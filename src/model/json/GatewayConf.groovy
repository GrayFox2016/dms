package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class GatewayConf implements JSONFiled {

    Integer clusterId

    Integer frontendId

    Integer containerPrivatePort

    String healthCheckUri

    Integer healthCheckDelaySeconds

    Integer healthCheckIntervalSeconds

    Integer healthCheckTimeoutSeconds

    Integer healthCheckTotalTimes

    boolean asBoolean() {
        clusterId != null && frontendId != null
    }
}
