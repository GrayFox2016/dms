package model

import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record
import groovy.transform.CompileStatic

@CompileStatic
class ImageEnvDTO extends Record {
    
    Integer id

    String imageName

    String name

    String des

    String env

    @Override
    String pk() {
        'id'
    }
    
    @Override
    D useD() {
        new D(Ds.one(), new MySQLDialect())
    }    
}