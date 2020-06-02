package model

import groovy.transform.CompileStatic
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class ImageRegistryDTO extends Record {

    Integer id

    String name

    String url

    String loginUser

    String loginPassword

    Date updatedDate

    String trimScheme() {
        if (!url) {
            return null
        }
        url.replace('http://', '').replace('https://', '')
    }

    boolean anon() {
        'anon' == loginUser
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