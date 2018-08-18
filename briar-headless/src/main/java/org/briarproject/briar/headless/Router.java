package org.briarproject.briar.headless;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.javalin.Javalin;

@Singleton
public class Router {

	private final BriarService briarService;
	private final ForumController forumController;

	@Inject
	public Router(BriarService briarService, ForumController forumController) {
		this.briarService = briarService;
		this.forumController = forumController;
	}

	public void start() {
		briarService.start();

		Javalin app = Javalin.create()
				.port(7000)
				.disableStartupBanner()
				.enableStandardRequestLogging()
				.start();
		app.get("/forums", forumController::list);
		app.post("/forums", forumController::create);
	}

}
