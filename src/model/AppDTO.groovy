package model

import groovy.transform.CompileStatic
import model.json.*
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class AppDTO extends Record {
    @CompileStatic
    static enum Status {
        auto(0), manual(1)

        int val

        Status(int val) {
            this.val = val
        }
    }

    Integer id

    Integer clusterId

    Integer namespaceId

    String name

    String des

    AppConf conf

    LiveCheckConf liveCheckConf

    MonitorConf monitorConf

    ABConf abConf

    JobConf jobConf

    GatewayConf gatewayConf

    Integer status

    Date updatedDate

    boolean autoManage() {
        status == Status.auto.val
    }

    @Override
    String pk() {
        'id'
    }

    @Override
    D useD() {
        new D(Ds.one(), new MySQLDialect())
    }
}