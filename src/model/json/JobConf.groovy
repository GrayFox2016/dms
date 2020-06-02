package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class JobConf implements JSONFiled {
    String cronExp

    boolean isOn

    boolean asBoolean() {
        cronExp != null
    }
}
