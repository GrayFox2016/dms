package ctrl

import agent.Agent
import auth.LoginService
import auth.User
import model.AppDTO
import model.ClusterDTO
import model.NamespaceDTO
import model.UserPermitDTO
import org.apache.commons.codec.digest.DigestUtils
import org.segment.web.handler.ChainHandler
import spi.SpiSupport

def h = ChainHandler.instance

h.post('/login') { req, resp ->
    String user = req.param('user')
    String password = req.param('password')
    assert user && password

    LoginService loginService = SpiSupport.createLoginService()
    def u = loginService.login(user, password)
    req.session('user', u)
    resp.redirect('/admin/index.html')
}

h.get('/login/user') { req, resp ->
    req.session('user')
}

h.get('/logout') { req, resp ->
    req.removeSession('user')
    resp.redirect('/admin/login.html')
}

h.get('/agent/auth') { req, resp ->
    def clusterId = req.header(Agent.CLUSTER_ID_HEADER)
    def secret = req.param('secret')
    assert clusterId && secret
    def one = new ClusterDTO(id: clusterId as int).queryFields('secret').one() as ClusterDTO
    if (one.secret != secret) {
        resp.halt(403, 'secret not match')
    }
    resp.end DigestUtils.md5Hex(one.secret + req.host())
}

h.group('/permit') {
    h.get('/list') { req, resp ->
        def p = req.param('pageNum')
        int pageNum = p ? p as int : 1
        final int pageSize = 10

        def keyword = req.param('keyword')
        def permitType = req.param('permitType')
        def resourceId = req.param('resourceId')
        def pager = new UserPermitDTO().where('1=1').where(!!keyword, 'user like ?',
                '%' + keyword + '%').where(!!permitType, 'permit_type=?', permitType).
                where(!!resourceId, 'resource_id=?', resourceId as Integer).loadPager(pageNum, pageSize)
        if (pager.list) {
            def clusterList = new ClusterDTO().where('1=1').queryFields('id,name').loadList() as List<ClusterDTO>
            def namespaceList = new NamespaceDTO().where('1=1').queryFields('id,name').loadList() as List<NamespaceDTO>
            for (one in pager.list) {
                UserPermitDTO permit = one
                if (permit.permitType == 'cluster') {
                    def cluster = clusterList.find { it.id == permit.resourceId }
                    if (cluster) {
                        permit.prop('resourceName', cluster.name)
                        permit.prop('resourceDes', cluster.des)
                    }
                } else if (permit.permitType == 'namespace') {
                    def namespace = namespaceList.find { it.id == permit.resourceId }
                    if (namespace) {
                        permit.prop('resourceName', namespace.name)
                        permit.prop('resourceDes', namespace.des)
                    }
                }
            }

            def appPermitList = pager.list.findAll { one ->
                UserPermitDTO permit = one
                'app' == permit.permitType
            }
            if (appPermitList) {
                def appList = new AppDTO().whereIn('id', appPermitList.collect { one ->
                    UserPermitDTO permit = one
                    permit.resourceId
                }).loadList() as List<AppDTO>
                for (one in appPermitList) {
                    UserPermitDTO permit = one
                    def app = appList.find { it.id == permit.resourceId }
                    if (app) {
                        permit.prop('resourceName', app.name)
                        permit.prop('resourceDes', app.des)
                    }
                }
            }
        }
        pager.list = pager.list.collect { one ->
            UserPermitDTO permit = one
            permit.rawProps(true)
        }
        pager
    }.delete('/delete/:id') { req, resp ->
        def id = req.param(':id')
        assert id
        new UserPermitDTO(id: id as int).delete()
        [flag: true]
    }.post('/update') { req, resp ->
        def one = req.bodyAs(UserPermitDTO)
        assert one.user && one.permitType
        def u = req.session('user') as User
        one.createdUser = u.name
        one.updatedDate = new Date()
        if (one.id) {
            one.update()
            return [id: one.id]
        } else {
            def id = one.add()
            return [id: id]
        }
    }.get('/resource/list') { req, resp ->
        def permitType = req.param('permitType')
        if (User.PermitType.cluster.name() == permitType) {
            return new ClusterDTO().where('1=1').queryFields('id,name').loadList()
        } else if (User.PermitType.namespace.name() == permitType) {
            return new NamespaceDTO().where('1=1').queryFields('id,name').loadList()
        } else if (User.PermitType.app.name() == permitType) {
            return new AppDTO().where('1=1').queryFields('id,name').loadList()
        }
    }
}
