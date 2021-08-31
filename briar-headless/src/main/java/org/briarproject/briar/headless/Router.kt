package org.briarproject.briar.headless

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.core.security.AccessManager
import io.javalin.core.util.Header.AUTHORIZATION
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.headless.blogs.BlogController
import org.briarproject.briar.headless.contact.ContactController
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.forums.ForumController
import org.briarproject.briar.headless.messaging.MessagingController
import java.lang.Runtime.getRuntime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level.INFO
import java.util.logging.Logger.getLogger
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

private const val VERSION = "v1"
private const val WS = "/$VERSION/ws"

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

    internal fun start(authToken: String, port: Int, debug: Boolean): Javalin {
        briarService.start()
        getRuntime().addShutdownHook(Thread(this::stop))

        val accessManager = AccessManager { handler, ctx, _ ->
            when {
                ctx.header(AUTHORIZATION) == "Bearer $authToken" -> handler.handle(ctx)
                ctx.matchedPath() == WS -> handler.handle(ctx) // websockets use their own auth
                else -> ctx.status(401).result("Unauthorized")
            }
        }

        val app = Javalin.create { config ->
            config.showJavalinBanner = false
            config.accessManager(accessManager)
            if (debug) config.enableDevLogging()
        }.events { event ->
            event.serverStartFailed { serverStopped() }
            event.serverStopped { serverStopped() }
        }

        app.routes {
            path("/$VERSION") {
                path("/contacts") {
                    get { ctx -> contactController.list(ctx) }
                    path("add") {
                        path("link") {
                            get { ctx -> contactController.getLink(ctx) }
                        }
                        path("pending") {
                            get { ctx -> contactController.listPendingContacts(ctx) }
                            post { ctx -> contactController.addPendingContact(ctx) }
                            delete { ctx -> contactController.removePendingContact(ctx) }
                        }
                    }
                    path("/:contactId") {
                        delete { ctx -> contactController.delete(ctx) }
                    }
                    path("/:contactId/alias") {
                        put { ctx -> contactController.setContactAlias(ctx) }
                    }
                }
                path("/messages/:contactId") {
                    get { ctx -> messagingController.list(ctx) }
                    post { ctx -> messagingController.write(ctx) }
                }
                path("/messages/:contactId/read") {
                    post { ctx -> messagingController.markMessageRead(ctx) }
                }
                path("/messages/:contactId/all") {
                    delete { ctx -> messagingController.deleteAllMessages(ctx) }
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
        app.ws(WS) { ws ->
            if (logger.isLoggable(INFO)) ws.onConnect { ctx ->
                logger.info("Received websocket connection from ${ctx.session.remoteAddress}")
                logger.info("Waiting for authentication")
            }
            ws.onMessage { ctx ->
                val session = ctx.session
                if (ctx.message() == authToken && !webSocketController.sessions.contains(ctx)) {
                    logger.info("Authenticated websocket session with ${session.remoteAddress}")
                    webSocketController.sessions.add(ctx)
                } else {
                    logger.info("Invalid message received: ${ctx.message()}")
                    logger.info("Closing websocket connection with ${session.remoteAddress}")
                    session.close(1008, "Invalid Authentication Token")
                }
            }
            ws.onClose { ctx ->
                logger.info("Removing websocket connection with ${ctx.session.remoteAddress}")
                webSocketController.sessions.remove(ctx)
            }
        }
        return app.start(port)
    }

    private fun serverStopped() {
        stop()
        exitProcess(1)
    }

    internal fun stop() {
        if (!stopped.getAndSet(true)) {
            briarService.stop()
        }
    }

}

/**
 * Returns a [ContactId] from the "contactId" path parameter.
 *
 * @throws NotFoundResponse when contactId is not a number.
 */
fun Context.getContactIdFromPathParam(): ContactId {
    val contactString = pathParam("contactId")
    val contactInt = try {
        Integer.parseInt(contactString)
    } catch (e: NumberFormatException) {
        throw NotFoundResponse()
    }
    return ContactId(contactInt)
}

/**
 * Returns a String from the JSON field or throws [BadRequestResponse] if null or empty.
 */
fun Context.getFromJson(objectMapper: ObjectMapper, field: String): String {
    try {
        val jsonNode = objectMapper.readTree(body())
        if (!jsonNode.hasNonNull(field)) throw BadRequestResponse("'$field' missing in JSON")
        val result = jsonNode.get(field).asText()
        if (result == null || result.isEmpty()) throw BadRequestResponse("'$field' empty in JSON")
        return result
    } catch (e: JsonParseException) {
        throw BadRequestResponse("Invalid JSON")
    }
}
