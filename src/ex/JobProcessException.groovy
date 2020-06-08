package ex

import groovy.transform.CompileStatic

@CompileStatic
class JobProcessException extends RuntimeException {
    JobProcessException(String var1) {
        super(var1)
    }

    JobProcessException(String var1, Throwable var2) {
        super(var1, var2)
    }
}
