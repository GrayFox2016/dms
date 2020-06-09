package server.dns

import com.alibaba.fastjson.JSON
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client

import java.nio.charset.Charset

@CompileStatic
@Slf4j
class DnsOperator {

    private Client client
    private String prefix

    DnsOperator(Client client, String prefix = '/skydns') {
        this.client = client
        this.prefix = prefix
    }

    boolean put(String hostname, String ip, int ttl = 10) {
        def data = [host: ip, ttl: ttl]
        def key = ByteSequence.from(prefix + '/' + hostname.split(/\./).reverse().join('/'), Charset.defaultCharset())
        def value = ByteSequence.from(JSON.toJSONString(data), Charset.defaultCharset())
        try {
            def response = client.KVClient.put(key, value).get()
            log.debug '' + response
            true
        } catch (Exception e) {
            log.error('put dns record error - ' + hostname + ':' + ip, e)
            false
        }
    }

}
