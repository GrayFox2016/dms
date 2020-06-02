package model.json

import groovy.transform.CompileStatic

@CompileStatic
class PortMapping {
    static enum ListenType {
        tcp, udp
    }

    Integer privatePort

    Integer publicPort

    boolean isGenerateByHost = false

    ListenType listenType

    @Override
    boolean equals(Object obj) {
        if (!(obj instanceof PortMapping)) {
            return false
        }
        def one = (PortMapping) obj
        privatePort == one.privatePort && publicPort == one.publicPort &&
                isGenerateByHost == one.isGenerateByHost && listenType == one.listenType
    }
}
