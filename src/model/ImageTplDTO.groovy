package model

import groovy.transform.CompileStatic
import model.json.TplParamsConf
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class ImageTplDTO extends Record {

    static enum TplType {
        init, mount, checkPre, checkAfter
    }

    Integer id

    String imageName

    String name

    String des

    String tplType

    String mountDist

    Integer isParentDirMount

    String content

    TplParamsConf params

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