package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class ScriptPullContent implements JSONFiled {
    @CompileStatic
    static class ScriptPullOne {
        int id
        String name
        Date updatedDate
    }

    List<ScriptPullOne> list = []
}
