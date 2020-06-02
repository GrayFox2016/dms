package vendor

import auth.LoginService
import auth.User
import groovy.transform.CompileStatic

@CompileStatic
class MyLoginService implements LoginService {
    @Override
    User login(String user, String password) {
        def u = new User(name: user)
        u.permitList << User.PermitAdmin
        u
    }
}
