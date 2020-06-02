package model

import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record
import groovy.transform.CompileStatic

@CompileStatic
class AgentScriptDTO extends Record {
    
    Integer id

    String name

    String des

    String content

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