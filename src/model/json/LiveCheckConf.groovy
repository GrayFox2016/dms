package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class LiveCheckConf implements JSONFiled {
    public static final String SHELL_RESULT_OK = 'ok'

    Boolean isHttpRequest

    String httpRequestUri

    Integer httpTimeoutSeconds = 1

    Boolean isPortListen

    Integer port

    Boolean isShellScript

    String shellScript

    Integer intervalSeconds = 30

    boolean asBoolean() {
        isHttpRequest || isShellScript || isPortListen
    }

}
