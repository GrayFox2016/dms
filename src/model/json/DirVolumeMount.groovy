package model.json

import groovy.transform.CompileStatic

@CompileStatic
class DirVolumeMount {
    Integer nodeVolumeId

    String dir

    String dist

    @Override
    boolean equals(Object obj) {
        if (!(obj instanceof DirVolumeMount)) {
            return false
        }
        def one = (DirVolumeMount) obj
        nodeVolumeId == one.nodeVolumeId && dir == one.dir && dist == one.dist
    }
}
