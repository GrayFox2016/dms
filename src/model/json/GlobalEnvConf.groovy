package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class GlobalEnvConf implements JSONFiled {
    String dnsServer
    String allAppLogDir
    List<KVPair> skipConflictCheckVolumeDirList = []
    List<KVPair> envList = []
}
