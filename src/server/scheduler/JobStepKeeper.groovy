package server.scheduler

import com.alibaba.fastjson.JSON
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import model.AppJobLogDTO
import org.segment.d.D

@CompileStatic
@Slf4j
class JobStepKeeper {
    @CompileStatic
    static enum Step {
        chooseNode, pullImage, preCheck, createContainer, initContainer, startContainer,
        updateDns, afterCheck, addToGateway, stopAndRemoveContainer, removeFromGateway, done
    }

    @CompileStatic
    static class JobStep {
        Step step
        String message
        Date createdDate = new Date()
        String nodeIp
        int instanceIndex

        @Override
        String toString() {
            [createdDate.format(D.ymdhms), '' + nodeIp, '' + instanceIndex,
             step.name(), message].collect { it.padRight(20, ' ') }.join(' - ')
        }
    }

    int jobId
    String nodeIp
    int instanceIndex
    List<JobStep> stepList = []

    private long beginT = System.currentTimeMillis()

    static final String DONE_OK = 'ok'
    static final String DONE_FAIL = 'fail'

    JobStepKeeper next(Step step, String message = '', boolean isOk = true) {
        def costT = System.currentTimeMillis() - beginT
        String m = ('Cost Ms: ' + costT).padRight(20, ' ') + ' - ' + message + ' - ' +
                (isOk ? DONE_OK : DONE_FAIL)
        def jobStep = new JobStep(step: step, nodeIp: nodeIp, instanceIndex: instanceIndex, message: m)
        stepList << jobStep
        log.info jobStep.toString()

        String messageAll = JSON.toJSONString(stepList)

        def one = new AppJobLogDTO(jobId: jobId, instanceIndex: instanceIndex).
                queryFields('id').one() as AppJobLogDTO
        if (one) {
            one.message = messageAll
            one.createdDate = new Date()
            one.update()
        } else {
            new AppJobLogDTO(jobId: jobId, instanceIndex: instanceIndex, message: messageAll,
                    createdDate: new Date()).add()
        }

        beginT = System.currentTimeMillis()
        this
    }

    static boolean isJobLogDoneOk(AppJobLogDTO one) {
        if (!one || !one.message) {
            return false
        }
        one.message.readLines().findAll { it.trim() }[-1].contains(' - ' + DONE_OK)
    }
}
