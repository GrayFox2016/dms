package model

import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record
import groovy.transform.CompileStatic

@CompileStatic
class NodeDTO extends Record {
    
    Integer id

    Integer clusterId

    String ip

    String tags

    String agentVersion

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