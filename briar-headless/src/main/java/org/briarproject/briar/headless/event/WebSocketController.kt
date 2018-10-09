package org.briarproject.briar.headless.event

import io.javalin.websocket.WsSession
import org.briarproject.bramble.api.lifecycle.IoExecutor
import org.briarproject.briar.headless.json.JsonDict
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
interface WebSocketController {

    val sessions: MutableSet<WsSession>

    /**
     * Sends an event to all open sessions using the [IoExecutor].
     */
    fun sendEvent(name: String, obj: JsonDict)

}
