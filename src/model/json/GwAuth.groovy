package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class GwAuth implements JSONFiled {
    List<KVPair> basicList
}
