package ctrl

import auth.User
import model.GwClusterDTO
import model.GwFrontendDTO
import org.segment.web.handler.ChainHandler
import server.gateway.GatewayOperator

def h = ChainHandler.instance

h.group('/gw/cluster') {
    h.get('/list') { req, resp ->
        new GwClusterDTO().where('1=1').loadList()
    }.get('/list/simple') { req, resp ->
        new GwClusterDTO().where('1=1').queryFields('id,name,des').loadList()
    }.delete('/delete/:id') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def id = req.param(':id')
        assert id
        new GwClusterDTO(id: id as int).delete()
        [flag: true]
    }.post('/update') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def one = req.bodyAs(GwClusterDTO)
        assert one.name && one.zkNode && one.prefix
        one.updatedDate = new Date()
        if (one.id) {
            one.update()
            return [id: one.id]
        } else {
            def id = one.add()
            return [id: id]
        }
    }.get('/overview') { req, resp ->
        def clusterId = req.param('clusterId')
        assert clusterId

        def cid = clusterId as int
        def r = GatewayOperator.create(cid + 20000).getBackendListFromApi(cid)

        def list = new GwFrontendDTO(clusterId: clusterId as int).loadList() as List<GwFrontendDTO>
        list.collect {
            def apiBackendList = r[it.id]
            def serverList = it.backend.serverList
            serverList.each { server ->
                def one = apiBackendList.find { t -> t.url == server.url }
                if (!one) {
                    server.url += ' - !!! not found from api'
                } else {
                    if (one.weight != server.weight) {
                        server.url += (' - !!! weight api - ' + one.weight)
                    }
                }
            }
            apiBackendList.each { server ->
                def one = serverList.find { t -> t.url == server.url }
                if (!one) {
                    server.url += ' - !!! not found from local config'
                } else {
                    if (one.weight != server.weight) {
                        server.url += (' - !!! weight local - ' + one.weight)
                    }
                }
            }
            [id: it.id, name: it.name, des: it.des, conf: it.conf, serverList: serverList, apiBackendList: apiBackendList]
        }
    }
}

h.group('/gw/frontend') {
    h.get('/list') { req, resp ->
        def clusterId = req.param('clusterId')
        assert clusterId
        new GwFrontendDTO(clusterId: clusterId as int).loadList()
    }.get('/list/simple') { req, resp ->
        def clusterId = req.param('clusterId')
        assert clusterId
        new GwFrontendDTO(clusterId: clusterId as int).queryFields('id,name,des').loadList()
    }.delete('/delete/:id') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def id = req.param(':id')
        assert id
        new GwFrontendDTO(id: id as int).delete()
        [flag: true]
    }.post('/update') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def one = req.bodyAs(GwFrontendDTO)
        assert one.name && one.clusterId
        one.updatedDate = new Date()

        if (one.id) {
            one.update()
            GatewayOperator.create(one.id + 10000).updateFrontend(one)
            return [id: one.id]
        } else {
            def id = one.add()
            one.id = id
            GatewayOperator.create(one.id + 10000).updateFrontend(one)
            return [id: id]
        }
    }
}