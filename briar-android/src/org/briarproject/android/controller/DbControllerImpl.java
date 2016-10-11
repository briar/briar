package org.briarproject.android.controller;

import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

public class DbControllerImpl implements DbController {

	private static final Logger LOG =
			Logger.getLogger(DbControllerImpl.class.getName());

	private final Executor dbExecutor;
	private final LifecycleManager lifecycleManager;

	@Inject
	public DbControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager) {
		this.dbExecutor = dbExecutor;
		this.lifecycleManager = lifecycleManager;
	}

	@Override
	public void runOnDbThread(final Runnable task) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					task.run();
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}
}
