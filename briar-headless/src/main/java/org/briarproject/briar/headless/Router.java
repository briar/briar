package org.briarproject.briar.headless;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.briar.headless.blogs.BlogController;
import org.briarproject.briar.headless.forums.ForumController;

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
	private final ForumController forumController;
	private final BlogController blogController;

	@Inject
	public Router(BriarService briarService, ForumController forumController,
			BlogController blogController) {
		this.briarService = briarService;
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
