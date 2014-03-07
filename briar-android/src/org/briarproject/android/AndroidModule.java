package org.briarproject.android;

import static android.content.Context.MODE_PRIVATE;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Singleton;

import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.ui.UiCallback;

import android.app.Application;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class AndroidModule extends AbstractModule {

	private final ExecutorService databaseUiExecutor;
	private final UiCallback uiCallback;

	public AndroidModule() {
		// The queue is unbounded, so tasks can be dependent
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Use a single thread so DB accesses from the UI don't overlap
		databaseUiExecutor = new ThreadPoolExecutor(1, 1, 60, SECONDS, queue,
				policy);
		// Use a dummy UI callback
		uiCallback = new UiCallback() {

			public int showChoice(String[] options, String... message) {
				throw new UnsupportedOperationException();
			}

			public boolean showConfirmationMessage(String... message) {
				throw new UnsupportedOperationException();
			}

			public void showMessage(String... message) {
				throw new UnsupportedOperationException();
			}			
		};
	}

	protected void configure() {
		bind(AndroidExecutor.class).to(AndroidExecutorImpl.class).in(
				Singleton.class);
		bind(AndroidNotificationManager.class).to(
				AndroidNotificationManagerImpl.class).in(Singleton.class);
		bind(ReferenceManager.class).to(ReferenceManagerImpl.class).in(
				Singleton.class);
		bind(UiCallback.class).toInstance(uiCallback);
	}

	@Provides @Singleton @DatabaseUiExecutor
	Executor getDatabaseUiExecutor(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(databaseUiExecutor);
		return databaseUiExecutor;
	}

	@Provides @Singleton
	DatabaseConfig getDatabaseConfig(final Application app) {
		final File dir = app.getApplicationContext().getDir("db", MODE_PRIVATE);
		return new DatabaseConfig() {

			private volatile byte[] key = null;

			public boolean databaseExists() {
				return dir.isDirectory() && dir.listFiles().length > 0;
			}

			public File getDatabaseDirectory() {
				return dir;
			}

			public void setEncryptionKey(byte[] key) {
				this.key = key;
			}

			public byte[] getEncryptionKey() {
				return key;
			}

			public long getMaxSize() {
				return Long.MAX_VALUE;
			}
		};
	}
}
