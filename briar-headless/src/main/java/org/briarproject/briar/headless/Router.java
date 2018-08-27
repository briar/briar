package org.briarproject.briar.headless;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.briar.headless.blogs.BlogController;
import org.briarproject.briar.headless.contact.ContactController;
import org.briarproject.briar.headless.forums.ForumController;
import org.briarproject.briar.headless.messaging.MessagingController;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.javalin.Javalin;

import static io.javalin.ApiBuilder.get;
import static io.javalin.ApiBuilder.path;
import static io.javalin.ApiBuilder.post;
import static io.javalin.event.EventType.SERVER_START_FAILED;
import static io.javalin.event.EventType.SERVER_STOPPED;
import static java.lang.Runtime.getRuntime;

@Immutable
@Singleton
@MethodsNotNullByDefault
@ParametersAreNonnullByDefault
public class Router {

	private final BriarService briarService;
	private final ContactController contactController;
	private final MessagingController messagingController;
	private final ForumController forumController;
	private final BlogController blogController;

	@Inject
	public Router(BriarService briarService,
			ContactController contactController,
			MessagingController messagingController,
			ForumController forumController,
			BlogController blogController) {
		this.briarService = briarService;
		this.contactController = contactController;
		this.messagingController = messagingController;
		this.forumController = forumController;
		this.blogController = blogController;
	}

	public void start() {
		briarService.start();
		getRuntime().addShutdownHook(new Thread(briarService::stop));

		Javalin app = Javalin.create()
				.port(7000)
				.disableStartupBanner()
				.enableStandardRequestLogging()
				.enableRouteOverview("/")
				.enableDynamicGzip()
				.event(SERVER_START_FAILED, event -> briarService.stop())
				.event(SERVER_STOPPED, event -> briarService.stop())
				.start();

		app.routes(() -> {
			path("/contacts", () -> get(contactController::list));
			path("/messages/:contactId", () -> {
				get(messagingController::list);
				post(messagingController::write);
			});
			path("/forums", () -> {
				get(forumController::list);
				post(forumController::create);
			});
			path("/blogs", () -> path("/posts", () -> {
				get(blogController::listPosts);
				post(blogController::createPost);
			}));
		});
	}

}
