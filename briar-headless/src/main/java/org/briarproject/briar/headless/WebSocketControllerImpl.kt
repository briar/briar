package org.briarproject.briar.headless

import io.javalin.json.JavalinJson
import io.javalin.websocket.WsSession
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class WebSocketControllerImpl @Inject constructor() : WebSocketController {

    override val sessions: MutableSet<WsSession> = ConcurrentHashMap.newKeySet<WsSession>()

    override fun sendEvent(name: String, obj: Any) {
        sessions.forEach { session ->
            val event = OutputEvent(name, obj)
            val json = JavalinJson.toJsonMapper.map(event)
            session.send(json)
        }
    }

}

@Immutable
@Suppress("unused")
internal class OutputEvent(val name: String, val data: Any) {
    val type = "event"
}
