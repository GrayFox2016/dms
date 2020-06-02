package ctrl

import auth.User
import model.ClusterDTO
import model.NamespaceDTO
import model.NodeDTO
import org.segment.web.handler.ChainHandler

def h = ChainHandler.instance

h.group('/cluster') {
    h.get('/list') { req, resp ->
        new ClusterDTO().where('1=1').loadList()
    }.get('/list/simple') { req, resp ->
        new ClusterDTO().where('1=1').queryFields('id,name,des').loadList()
    }.delete('/delete/:id') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def id = req.param(':id')
        assert id

        // check if has namespace
        def namespaceList = new NamespaceDTO(clusterId: id as int).queryFields('id,name').loadList() as List<NamespaceDTO>
        if (namespaceList) {
            resp.halt(500, 'has namespace - ' + namespaceList.collect { it.name })
        }
        new ClusterDTO(id: id as int).delete()
        [flag: true]
    }.post('/update') { req, resp ->
        def one = req.bodyAs(ClusterDTO)
        assert one.name && one.secret
        one.updatedDate = new Date()
        if (one.id) {
            User u = req.session('user')
            if (!u.isAccessCluster(one.id)) {
                resp.halt(403, 'not this cluster manager')
            }

            one.update()
            return [id: one.id]
        } else {
            User u = req.session('user')
            if (!u.isAdmin()) {
                resp.halt(403, 'not admin')
            }

            def id = one.add()
            return [id: id]
        }
    }.get('/one/:id') { req, resp ->
        def id = req.param(':id')
        assert id
        def one = new ClusterDTO(id: id as int).one()
        def nodeList = new NodeDTO(clusterId: id as int).loadList()
        [one: one, nodeList: nodeList]
    }
}