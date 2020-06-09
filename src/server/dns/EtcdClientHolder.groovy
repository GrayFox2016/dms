package server.dns


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client

import java.nio.charset.Charset

@CompileStatic
@Singleton
@Slf4j
class EtcdClientHolder {
    private Map<String, Client> cached = [:]

    synchronized Client create(String endpoints, String user = null, String password = null) {
        def client = cached[endpoints]
        if (client) {
            return client
        }

        def builder = Client.builder().endpoints(endpoints)
        if (user) {
            builder.user(ByteSequence.from(user, Charset.defaultCharset()))
        }
        if (password) {
            builder.password(ByteSequence.from(password, Charset.defaultCharset()))
        }
        def one = builder.build()
        log.info 'connected - ' + endpoints
        cached[endpoints] = one
        one
    }

    void close() {
        cached.each { k, v ->
            log.info 'ready to close etcd client - ' + k
            v.close()
            log.info 'done close etcd client - ' + k
        }
    }
}
