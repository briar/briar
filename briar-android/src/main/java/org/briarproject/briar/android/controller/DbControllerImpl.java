package org.briarproject.briar.android.controller;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
public class DbControllerImpl implements DbController {

	private static final Logger LOG =
			Logger.getLogger(DbControllerImpl.class.getName());

	protected final Executor dbExecutor;
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
