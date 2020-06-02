package server.scheduler

import com.alibaba.fastjson.JSONObject
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.AppDTO
import model.AppJobDTO

@CompileStatic
@Slf4j
class RemoveProcessor extends CreateProcessor {
    @Override
    void process(AppJobDTO job, AppDTO app, List<JSONObject> containerList) {
        int toContainerNumber = job.param('toContainerNumber') as int
        if (toContainerNumber >= containerList.size()) {
            log.warn 'remove container skip - ' + containerList.size() + ' -> ' + toContainerNumber
            return
        }
        containerList[toContainerNumber..-1].each { x ->
            stopOneContainer(job.id, app, x)
        }
    }

}
