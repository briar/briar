package org.briarproject.briar.headless.event

import io.javalin.json.JavalinJson.toJson
import io.javalin.websocket.WsSession
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class WebSocketControllerImpl @Inject constructor() :
    WebSocketController {

    override val sessions: MutableSet<WsSession> = ConcurrentHashMap.newKeySet<WsSession>()

    override fun sendEvent(name: String, obj: Any) {
        sessions.forEach { session ->
            val event = OutputEvent(name, obj)
            session.send(toJson(event))
        }
    }

}
