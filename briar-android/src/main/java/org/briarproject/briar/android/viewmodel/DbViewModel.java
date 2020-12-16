package org.briarproject.briar.android.viewmodel;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import static java.util.logging.Logger.getLogger;

@Immutable
@NotNullByDefault
public abstract class DbViewModel extends AndroidViewModel {

	private static final Logger LOG = getLogger(DbViewModel.class.getName());

	@DatabaseExecutor
	private final Executor dbExecutor;
	private final LifecycleManager lifecycleManager;

	public DbViewModel(
			@NonNull Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager) {
		super(application);
		this.dbExecutor = dbExecutor;
		this.lifecycleManager = lifecycleManager;
	}

	public void runOnDbThread(Runnable task) {
		dbExecutor.execute(() -> {
			try {
				lifecycleManager.waitForDatabase();
				task.run();
			} catch (InterruptedException e) {
				LOG.warning("Interrupted while waiting for database");
				Thread.currentThread().interrupt();
			}
		});
	}

}
