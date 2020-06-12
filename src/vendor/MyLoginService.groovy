package vendor

import auth.LoginService
import auth.User
import com.github.kevinsawicki.http.HttpRequest
import common.Conf
import ex.HttpInvokeException
import groovy.transform.CompileStatic
import model.UserPermitDTO

@CompileStatic
class MyLoginService implements LoginService {
    @Override
    User login(String user, String password) {
        def u = new User(name: user)
        def c = Conf.instance
        if (c.get('login.url')) {
            def req = HttpRequest.post(c.get('login.url'))
                    .connectTimeout(c.getInt('login.connectTimeout', 1000))
                    .readTimeout(c.getInt('login.readTimeout', 1000))
                    .form([user: user, password: password])
            if (200 != req.code()) {
                throw new HttpInvokeException('login fail ' + req.body())
            }
            def list = new UserPermitDTO(user: user).loadList(10) as List<UserPermitDTO>
            u.permitList.addAll(list.collect {
                new User.Permit(User.PermitType.valueOf(it.permitType), it.resourceId)
            })
            return u
        } else {
            u.permitList << User.PermitAdmin
            u
        }
    }
}
