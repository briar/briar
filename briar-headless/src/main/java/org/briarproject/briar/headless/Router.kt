package org.briarproject.briar.headless

import io.javalin.Javalin
import io.javalin.JavalinEvent.SERVER_START_FAILED
import io.javalin.JavalinEvent.SERVER_STOPPED
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.core.util.ContextUtil
import io.javalin.core.util.Header
import org.briarproject.briar.headless.blogs.BlogController
import org.briarproject.briar.headless.contact.ContactController
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.forums.ForumController
import org.briarproject.briar.headless.messaging.MessagingController
import java.lang.Runtime.getRuntime
import java.util.logging.Logger
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Immutable
@Singleton
internal class Router @Inject
constructor(
    private val briarService: BriarService,
    private val webSocketController: WebSocketController,
    private val contactController: ContactController,
    private val messagingController: MessagingController,
    private val forumController: ForumController,
    private val blogController: BlogController
) {

    private val logger: Logger = Logger.getLogger(this.javaClass.name)

    fun start(authToken: String, port: Int, debug: Boolean) {
        briarService.start()
        getRuntime().addShutdownHook(Thread(Runnable { briarService.stop() }))

        val app = Javalin.create()
            .port(port)
            .disableStartupBanner()
            .enableCaseSensitiveUrls()
            .enableRouteOverview("/")
            .event(SERVER_START_FAILED) { stop() }
            .event(SERVER_STOPPED) { stop() }
        if (debug) app.enableDebugLogging()
        app.start()

        app.accessManager { handler, ctx, _ ->
            if (ctx.header("Authorization") == "Bearer $authToken") {
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
                val authHeader = session.header(Header.AUTHORIZATION)
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

    private fun stop() {
        briarService.stop()
        exitProcess(0)
    }

}
