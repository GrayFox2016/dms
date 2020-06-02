package model.json

import groovy.transform.CompileStatic

@CompileStatic
class GwFrontendRateLimitConf {
    int period
    int average
    int burst
}
