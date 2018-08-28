package org.briarproject.briar.headless

import io.javalin.websocket.WsSession

interface WebSocketController {

    val sessions: MutableSet<WsSession>

    fun sendEvent(name: String, obj: Any)

}
