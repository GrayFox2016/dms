package server.scheduler

import com.alibaba.fastjson.JSONObject
import groovy.transform.CompileStatic
import model.AppDTO
import model.AppJobDTO

@CompileStatic
interface GuardianProcessor {
    void process(AppJobDTO job, AppDTO app, List<JSONObject> containerList)
}
