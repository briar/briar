package org.briarproject.briar.headless

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.BadRequestResponse
import io.javalin.Context
import io.javalin.Javalin
import io.javalin.JavalinEvent.SERVER_START_FAILED
import io.javalin.JavalinEvent.SERVER_STOPPED
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.core.util.ContextUtil
import io.javalin.core.util.Header.AUTHORIZATION
import org.briarproject.briar.headless.blogs.BlogController
import org.briarproject.briar.headless.contact.ContactController
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.forums.ForumController
import org.briarproject.briar.headless.messaging.MessagingController
import java.lang.Runtime.getRuntime
import java.lang.System.exit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger.getLogger
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class Router
@Inject
constructor(
    private val briarService: BriarService,
    private val webSocketController: WebSocketController,
    private val contactController: ContactController,
    private val messagingController: MessagingController,
    private val forumController: ForumController,
    private val blogController: BlogController
) {

    private val logger = getLogger(Router::javaClass.name)
    private val stopped = AtomicBoolean(false)

    fun start(authToken: String, port: Int, debug: Boolean) {
        briarService.start()
        getRuntime().addShutdownHook(Thread(this::stop))

        val app = Javalin.create()
            .port(port)
            .disableStartupBanner()
            .enableCaseSensitiveUrls()
            .event(SERVER_START_FAILED) {serverStopped() }
            .event(SERVER_STOPPED) { serverStopped() }
        if (debug) app.enableDebugLogging()
        app.start()

        app.accessManager { handler, ctx, _ ->
            if (ctx.header(AUTHORIZATION) == "Bearer $authToken") {
                handler.handle(ctx)
            } else {
                ctx.status(401).result("Unauthorized")
            }
        }
        app.routes {
            path("/v1") {
                path("/contacts") {
                    get { ctx -> contactController.list(ctx) }
                }
                path("/messages/:contactId") {
                    get { ctx -> messagingController.list(ctx) }
                    post { ctx -> messagingController.write(ctx) }
                }
                path("/forums") {
                    get { ctx -> forumController.list(ctx) }
                    post { ctx -> forumController.create(ctx) }
                }
                path("/blogs") {
                    path("/posts") {
                        get { ctx -> blogController.listPosts(ctx) }
                        post { ctx -> blogController.createPost(ctx) }
                    }
                }
            }
        }
        app.ws("/v1/ws") { ws ->
            ws.onConnect { session ->
                val authHeader = session.header(AUTHORIZATION)
                val token = ContextUtil.getBasicAuthCredentials(authHeader)?.username
                if (authToken == token) {
                    logger.info("Adding websocket session with ${session.remoteAddress}")
                    webSocketController.sessions.add(session)
                } else {
                    logger.info("Closing websocket connection with ${session.remoteAddress}")
                    session.close(1008, "Invalid Authentication Token")
                }
            }
            ws.onClose { session, _, _ ->
                logger.info("Removing websocket connection with ${session.remoteAddress}")
                webSocketController.sessions.remove(session)
            }
        }
    }

    private fun serverStopped() {
        stop()
        exit(1)
    }

    private fun stop() {
        if (!stopped.getAndSet(true)) {
            briarService.stop()
        }
    }

}

/**
 * Returns a String from the JSON field or throws [BadRequestResponse] if null or empty.
 */
fun Context.getFromJson(field: String) : String {
    try {
        // TODO use a static object mapper to avoid re-initializations
        val jsonNode = ObjectMapper().readTree(body())
        if (!jsonNode.hasNonNull(field)) throw BadRequestResponse("'$field' missing in JSON")
        val result = jsonNode.get(field).asText()
        if (result == null || result.isEmpty()) throw BadRequestResponse("'$field' empty in JSON")
        return result
    } catch (e: JsonParseException) {
        throw BadRequestResponse("Invalid JSON")
    }
}
