package ctrl

import auth.User
import common.Conf
import model.AgentScriptDTO
import model.AgentScriptPullLogDTO
import model.json.ScriptPullContent
import org.segment.web.handler.ChainHandler
import org.slf4j.LoggerFactory
import server.AgentCaller

def h = ChainHandler.instance

def log = LoggerFactory.getLogger(this.getClass())

h.group('/agent/script') {
    h.get('/pull/log') { req, resp ->
        new AgentScriptPullLogDTO().where('1=1').loadList()
    }.get('/pull/test') { req, resp ->
        def content = new ScriptPullContent()
        content.list << new ScriptPullContent.ScriptPullOne(id: 1, name: 'test', updatedDate: new Date())
        content.list << new ScriptPullContent.ScriptPullOne(id: 2, name: 'test2', updatedDate: new Date())
        new AgentScriptPullLogDTO(agentHost: req.host(), content: content, createdDate: new Date()).add()
        'ok'
    }.get('/list') { req, resp ->
        def p = req.param('pageNum')
        int pageNum = p ? p as int : 1
        final int pageSize = 10

        def keyword = req.param('keyword')
        new AgentScriptDTO().where('1=1').where(!!keyword, '(name like ?) or (des like ?)',
                '%' + keyword + '%', '%' + keyword + '%').loadPager(pageNum, pageSize)
    }.delete('/delete/:id') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def id = req.param(':id')
        assert id
        new AgentScriptDTO(id: id as int).delete()
        [flag: true]
    }.post('/update') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def one = req.bodyAs(AgentScriptDTO)
        assert one.name && one.content
        one.updatedDate = new Date()
        if (one.id) {
            one.update()
            return [id: one.id]
        } else {
            def id = one.add()
            return [id: id]
        }
    }.get('/update/batch') { req, resp ->
        User u = req.session('user')
        if (!u.isAdmin()) {
            resp.halt(403, 'not admin')
        }

        def scriptNameGiven = req.param('scriptName')

        def scriptFileList = new File(Conf.instance.projectPath('/src/script')).listFiles()
        scriptFileList.each { f ->
            if (f.isDirectory()) {
                return
            }
            String scriptName = org.segment.d.D.toUnderline(f.name.split(/\./)[0]).replaceAll('_', ' ')
            if (scriptNameGiven && scriptNameGiven != scriptName) {
                return
            }
            def one = new AgentScriptDTO(name: scriptName).queryFields('id').one() as AgentScriptDTO
            if (one) {
                one.des = scriptName
                one.content = f.text
                one.updatedDate = new Date()
                one.update()
                log.info 'done update script - ' + scriptName
            } else {
                def add = new AgentScriptDTO()
                add.name = scriptName
                add.des = scriptName
                add.content = f.text
                add.updatedDate = new Date()
                add.add()
                log.info 'done add script - ' + scriptName
            }
        }
        [flag: true]
    }.options('/exe') { req, resp ->
        User user = req.session('user')
        if (!user.isAdmin()) {
            resp.halt(500, 'not admin')
        }

        Map params
        if (req.method() == 'GET') {
            params = [:]
            req.raw().getParameterNames().each {
                params[it] = req.param(it)
            }
        } else {
            params = req.bodyAs()
        }

        def nodeIp = params.nodeIp
        def scriptName = params.scriptName
        assert nodeIp && scriptName

        def r = AgentCaller.instance.agentScriptExe(nodeIp, scriptName, params)
        resp.end r.toJSONString()
    }
}

// for script holder
h.post('/api/agent/script/pull') { req, resp ->
    HashMap map = req.bodyAs()
    def list = new AgentScriptDTO().where('1=1').loadList() as List<AgentScriptDTO>
    // compare updated date
    def r = []
    def content = new ScriptPullContent()

    list.findAll {
        def name = it.name
        def time = map[name] as Long
        time == null || time != it.updatedDate.time
    }.each {
        r << [name: it.name, content: it.content, updatedDate: it.updatedDate]
        content.list << new ScriptPullContent.ScriptPullOne(id: it.id, name: it.name, updatedDate: it.updatedDate)
    }

    String host = req.host()
    def oneLog = new AgentScriptPullLogDTO(agentHost: host).queryFields('id').one() as AgentScriptPullLogDTO
    if (oneLog) {
        oneLog.content = content
        oneLog.createdDate = new Date()
        oneLog.update()
    } else {
        new AgentScriptPullLogDTO(agentHost: host, content: content, createdDate: new Date()).add()
    }
    r
}