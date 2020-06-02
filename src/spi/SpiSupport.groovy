package spi

import auth.DefaultLoginService
import auth.LoginService
import conf.DataSourceCreator
import conf.DefaultLocalH2DataSourceCreator
import groovy.transform.CompileStatic
import server.OneLock
import server.hpa.DefaultScaleStrategy
import server.hpa.ScaleStrategy

@CompileStatic
class SpiSupport {
    static OneLock createLock() {
        def list = ServiceLoader.load(OneLock)
        list[0]
    }

    static LoginService createLoginService() {
        ServiceLoader.load(LoginService).find { it.class.name.startsWith('vendor') } as LoginService
                ?: new DefaultLoginService()
    }

    static DataSourceCreator createDataSourceCreator() {
        ServiceLoader.load(DataSourceCreator).find { it.class.name.startsWith('vendor') } as DataSourceCreator
                ?: new DefaultLocalH2DataSourceCreator()
    }

    static ScaleStrategy createScaleStrategy() {
        ServiceLoader.load(ScaleStrategy).find { it.class.name.startsWith('vendor') } as ScaleStrategy
                ?: new DefaultScaleStrategy()
    }
}
