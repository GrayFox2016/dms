package model.json

import groovy.transform.CompileStatic
import org.segment.d.json.JSONFiled

@CompileStatic
class GwFrontendConf implements JSONFiled {
    boolean passHostHeader

    String extractorFunc

    String extractorFuncHeaderName

    List<GwFrontendRateLimitConf> rateLimitConfList

    List<GwFrontendRuleConf> ruleConfList
}
