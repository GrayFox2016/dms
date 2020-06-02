package model

import groovy.transform.CompileStatic
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class UserPermitDTO extends Record {
    
    Integer id

    String user

    String createdUser

    String permitType

    Integer resourceId

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