package model

import groovy.transform.CompileStatic
import model.json.GwAuth
import model.json.GwBackend
import model.json.GwFrontendConf
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class GwFrontendDTO extends Record {

    Integer id

    Integer clusterId

    String name

    String des

    GwBackend backend

    GwAuth auth

    Integer priority

    GwFrontendConf conf

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