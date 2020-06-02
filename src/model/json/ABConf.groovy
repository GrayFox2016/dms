package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class ABConf implements JSONFiled {
    Integer containerNumber

    String image

    Integer weight

    boolean asBoolean() {
        image != null
    }
}
