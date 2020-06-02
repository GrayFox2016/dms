package server

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class LocalOneLock implements OneLock {
    String lockKey

    boolean lock() {
        true
    }

    void unlock() {}

    boolean exe(Closure closure) {
        closure.call()
        true
    }
}
