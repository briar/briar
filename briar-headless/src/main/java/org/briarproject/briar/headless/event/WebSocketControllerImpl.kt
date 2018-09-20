package org.briarproject.briar.headless.event

import io.javalin.json.JavalinJson.toJson
import io.javalin.websocket.WsSession
import org.briarproject.bramble.util.LogUtils.logException
import org.eclipse.jetty.websocket.api.WebSocketException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class WebSocketControllerImpl @Inject constructor() :
    WebSocketController {

    private val logger: Logger = Logger.getLogger(this.javaClass.name)

    override val sessions: MutableSet<WsSession> = ConcurrentHashMap.newKeySet<WsSession>()

    override fun sendEvent(name: String, obj: Any) {
        sessions.forEach { session ->
            val event = OutputEvent(name, obj)
            try {
                session.send(toJson(event))
            } catch (e: WebSocketException) {
                logException(logger, Level.WARNING, e)
            } catch (e: IOException) {
                logException(logger, Level.WARNING, e)
            }
        }
    }

}
