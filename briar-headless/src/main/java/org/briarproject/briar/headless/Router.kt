package org.briarproject.briar.headless

import io.javalin.Javalin
import io.javalin.JavalinEvent.SERVER_START_FAILED
import io.javalin.JavalinEvent.SERVER_STOPPED
import io.javalin.apibuilder.ApiBuilder.*
import org.briarproject.briar.headless.blogs.BlogController
import org.briarproject.briar.headless.contact.ContactController
import org.briarproject.briar.headless.forums.ForumController
import org.briarproject.briar.headless.messaging.MessagingController
import java.lang.Runtime.getRuntime
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Immutable
@Singleton
class Router @Inject
constructor(
    private val briarService: BriarService,
    private val contactController: ContactController,
    private val messagingController: MessagingController,
    private val forumController: ForumController,
    private val blogController: BlogController
) {

    fun start() {
        briarService.start()
        getRuntime().addShutdownHook(Thread(Runnable { briarService.stop() }))

        val app = Javalin.create()
            .port(7000)
            .disableStartupBanner()
            .enableDebugLogging()
            .enableCaseSensitiveUrls()
            .enableRouteOverview("/")
            .event(SERVER_START_FAILED) { stop() }
            .event(SERVER_STOPPED) { stop() }
            .start()

        app.routes {
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

    private fun stop() {
        briarService.stop()
        exitProcess(1)
    }

}
