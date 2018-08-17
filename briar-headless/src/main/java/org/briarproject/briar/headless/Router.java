package org.briarproject.briar.headless;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.javalin.Javalin;

@Singleton
public class Router {

	private final AccountManager accountManager;
	private final LifecycleManager lifecycleManager;
	private final ForumController forumController;

	@Inject
	public Router(AccountManager accountManager,
			LifecycleManager lifecycleManager,
			ForumController forumController) {
		this.accountManager = accountManager;
		this.lifecycleManager = lifecycleManager;
		this.forumController = forumController;
	}

	public void start() {
		if (accountManager.accountExists()) {
			accountManager.signIn("test");
		} else {
			accountManager.createAccount("test", "test");
		}
		assert accountManager.getDatabaseKey() != null;

		lifecycleManager.startServices(accountManager.getDatabaseKey());

		Javalin app = Javalin.create()
				.port(7000)
				.disableStartupBanner()
				.enableStandardRequestLogging()
				.start();
		app.get("/forums", forumController::list);
		app.post("/forums", forumController::create);
	}

}
