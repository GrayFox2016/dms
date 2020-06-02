package conf

import groovy.transform.CompileStatic
import org.segment.d.Ds

@CompileStatic
interface DataSourceCreator {
    Ds create()
}