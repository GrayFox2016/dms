package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class TplParamsConf implements JSONFiled {
    @CompileStatic
    static class TplParam {
        String name
        String type
        String defaultValue
    }

    List<TplParam> paramList = []
}
