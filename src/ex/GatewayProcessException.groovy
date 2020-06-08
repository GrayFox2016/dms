package ex

import groovy.transform.CompileStatic

@CompileStatic
class GatewayProcessException extends JobProcessException {
    GatewayProcessException(String var1) {
        super(var1)
    }

    GatewayProcessException(String var1, Throwable var2) {
        super(var1, var2)
    }
}
