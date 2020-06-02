package model

import groovy.transform.CompileStatic
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class EventDTO extends Record {
    Integer id

    String type

    String reason

    String result

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