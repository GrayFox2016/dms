package model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import groovy.transform.CompileStatic
import org.segment.d.D
import org.segment.d.Ds
import org.segment.d.MySQLDialect
import org.segment.d.Record

@CompileStatic
class AppJobDTO extends Record {
    @CompileStatic
    static enum Status {
        created(0), processing(1), failed(-1), done(10)

        int val

        Status(int val) {
            this.val = val
        }
    }

    @CompileStatic
    static enum JobType {
        create(1), remove(2), scroll(3)

        int val

        JobType(int val) {
            this.val = val
        }
    }

    Integer id

    Integer appId

    Integer status

    Integer failNum

    Integer jobType

    String message

    String params

    Date createdDate

    Date updatedDate

    Object param(String key) {
        if (!params) {
            return null
        }
        JSON.parseObject(params).get(key)
    }

    AppJobDTO addParam(String key, Object value) {
        JSONObject obj = params ? JSON.parseObject(params) : new JSONObject()
        obj[key] = value
        params = JSON.toJSONString(obj)
        this
    }

    @Override
    String pk() {
        'id'
    }

    @Override
    D useD() {
        new D(Ds.one(), new MySQLDialect())
    }
}