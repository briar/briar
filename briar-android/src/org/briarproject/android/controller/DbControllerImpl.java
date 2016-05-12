package org.briarproject.android.controller;

import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

public class DbControllerImpl implements DbController {

	private static final Logger LOG =
			Logger.getLogger(DbControllerImpl.class.getName());

	// Fields that are accessed from background threads must be volatile
	@Inject
	@DatabaseExecutor
	protected volatile Executor dbExecutor;
	@Inject
	protected volatile LifecycleManager lifecycleManager;

	@Inject
	public DbControllerImpl() {

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
