package model

import groovy.transform.CompileStatic
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class GwClusterDTO extends Record {

    Integer id

    String name

    String des

    String serverUrl

    Integer serverPort

    Integer dashboardPort

    String zkNode

    String prefix

    Date createdDate

    Date updatedDate

    @Override
    String pk() {
        'id'
    }

    @Override
    D useD() {
        new D(Ds.one(), new MySQLDialect())
    }
}