package model

import groovy.transform.CompileStatic
import model.json.ScriptPullContent
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class AgentScriptPullLogDTO extends Record {
    Integer id

    String agentHost

    ScriptPullContent content

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