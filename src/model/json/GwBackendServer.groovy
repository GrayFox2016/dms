package model.json

import groovy.transform.CompileStatic

@CompileStatic
class GwBackendServer {
    String url
    int weight

    @Override
    String toString() {
        "${url}/${weight}".toString()
    }
}
