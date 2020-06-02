package common

import com.alibaba.fastjson.JSON
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j
import model.EventDTO

@CompileStatic
@Builder
@Slf4j
class Event {
    static enum Type {
        cluster, node, app
    }

    Integer id

    Type type

    String reason

    String result

    String message

    Date createdDate

    Event log(String message = '') {
        this.message = message
        log.info("{}/{}/{} - {}", type, reason, result, message)
        this
    }

    EventDTO toDto() {
        new EventDTO(id: id, type: type?.name(), reason: reason, result: result,
                message: message, createdDate: new Date())
    }

    @Override
    String toString() {
        JSON.toJSONString(this)
    }
}