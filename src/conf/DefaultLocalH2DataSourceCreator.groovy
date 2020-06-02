package conf

import common.Conf
import groovy.transform.CompileStatic
import org.segment.d.Ds

@CompileStatic
class DefaultLocalH2DataSourceCreator implements DataSourceCreator {
    @Override
    Ds create() {
        String dbDataDir = Conf.instance.getString('dbDataDir', '/var/dms/data')
        Ds.h2Local(dbDataDir)
    }
}
