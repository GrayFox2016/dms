package model

import groovy.transform.CompileStatic
import model.json.GlobalEnvConf
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class ClusterDTO extends Record {

    Integer id

    String name

    String des

    String secret

    GlobalEnvConf globalEnvConf

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