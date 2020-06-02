package model.json

import groovy.transform.CompileStatic

@CompileStatic
class KVPair<E> {
    String key
    String type
    E value

    @Override
    String toString() {
        "${key}:${value}/${type}"
    }

    @Override
    boolean equals(Object obj) {
        if (!(obj instanceof KVPair)) {
            return false
        }
        def one = (KVPair) obj
        key == one.key && type == one.type && value == one.value
    }
}
