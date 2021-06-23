package org.briarproject.bramble.api.lifecycle;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Wakeful;

import java.util.concurrent.ExecutorService;

/**
 * Manages the lifecycle of the app: opening and closing the
 * {@link DatabaseComponent} starting and stopping {@link Service Services},
 * and shutting down {@link ExecutorService ExecutorServices}.
 */
@NotNullByDefault
public interface LifecycleManager {

	/**
	 * The result of calling {@link #startServices(SecretKey)}.
	 */
	enum StartResult {
		ALREADY_RUNNING,
		CLOCK_ERROR,
		DB_ERROR,
		DATA_TOO_OLD_ERROR,
		DATA_TOO_NEW_ERROR,
		SERVICE_ERROR,
		SUCCESS
	}

	/**
	 * The minimum reasonable value for the system clock, in milliseconds
	 * since the Unix epoch. {@link #startServices(SecretKey)} will return
	 * {@link StartResult#CLOCK_ERROR} if the system clock reports an earlier
	 * time.
	 * <p/>
	 * 1 Jan 2021, 00:00:00 UTC
	 */
	long MIN_REASONABLE_TIME_MS = 1_609_459_200_000L;

	/**
	 * The maximum reasonable value for the system clock, in milliseconds
	 * since the Unix epoch. {@link #startServices(SecretKey)} will return
	 * {@link StartResult#CLOCK_ERROR} if the system clock reports a later
	 * time.
	 * <p/>
	 * 1 Jan 2121, 00:00:00 UTC
	 */
	long MAX_REASONABLE_TIME_MS = 4_765_132_800_000L;

	/**
	 * The state the lifecycle can be in.
	 * Returned by {@link #getLifecycleState()}
	 */
	enum LifecycleState {

		STARTING, MIGRATING_DATABASE, COMPACTING_DATABASE, STARTING_SERVICES,
		RUNNING, STOPPING;

		public boolean isAfter(LifecycleState state) {
			return ordinal() > state.ordinal();
		}
	}

	/**
	 * Registers a hook to be called after the database is opened and before
	 * {@link Service services} are started. This method should be called
	 * before {@link #startServices(SecretKey)}.
	 */
	void registerOpenDatabaseHook(OpenDatabaseHook hook);

	/**
	 * Registers a {@link Service} to be started and stopped. This method
	 * should be called before {@link #startServices(SecretKey)}.
	 */
	void registerService(Service s);

	/**
	 * Registers an {@link ExecutorService} to be shut down. This method
	 * should be called before {@link #startServices(SecretKey)}.
	 */
	void registerForShutdown(ExecutorService e);

	/**
	 * Opens the {@link DatabaseComponent} using the given key and starts any
	 * registered {@link Service Services}.
	 */
	@Wakeful
	StartResult startServices(SecretKey dbKey);

	/**
	 * Stops any registered {@link Service Services}, shuts down any
	 * registered {@link ExecutorService ExecutorServices}, and closes the
	 * {@link DatabaseComponent}.
	 */
	@Wakeful
	void stopServices();

	/**
	 * Waits for the {@link DatabaseComponent} to be opened before returning.
	 */
	void waitForDatabase() throws InterruptedException;

	/**
	 * Waits for the {@link DatabaseComponent} to be opened and all registered
	 * {@link Service Services} to start before returning.
	 */
	void waitForStartup() throws InterruptedException;

	/**
	 * Waits for all registered {@link Service Services} to stop, all
	 * registered {@link ExecutorService ExecutorServices} to shut down, and
	 * the {@link DatabaseComponent} to be closed before returning.
	 */
	void waitForShutdown() throws InterruptedException;

	/**
	 * Returns the current state of the lifecycle.
	 */
	LifecycleState getLifecycleState();

	interface OpenDatabaseHook {
		/**
		 * Called when the database is being opened, before
		 * {@link #waitForDatabase()} returns.
		 *
		 * @param txn A read-write transaction
		 */
		@Wakeful
		void onDatabaseOpened(Transaction txn) throws DbException;
	}
}