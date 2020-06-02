package model

import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record
import groovy.transform.CompileStatic

@CompileStatic
class AppJobLogDTO extends Record {
    
    Integer id

    Integer jobId

    Integer instanceIndex

    String message

    Date createdDate

    @Override
    String pk() {
        'id'
    }
    
    @Override
    D useD() {
        new D(Ds.one(), new MySQLDialect())
    }    
}