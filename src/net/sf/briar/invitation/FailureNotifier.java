package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.invitation.ConnectionCallback;

class FailureNotifier extends Thread {

	private static final Logger LOG =
			Logger.getLogger(FailureNotifier.class.getName());

	private final Collection<Thread> workers;
	private final AtomicBoolean succeeded;
	private final ConnectionCallback callback;

	FailureNotifier(Collection<Thread> workers, AtomicBoolean succeeded,
			ConnectionCallback callback) {
		this.workers = workers;
		this.succeeded = succeeded;
		this.callback = callback;
	}

	@Override
	public void run() {
		if(LOG.isLoggable(INFO)) LOG.info(workers.size() + " workers");
		try {
			for(Thread worker : workers) worker.join();
			if(!succeeded.get()) {
				if(LOG.isLoggable(INFO)) LOG.info("No worker succeeded");
				callback.connectionNotEstablished();
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while waiting for workers");
			callback.connectionNotEstablished();
		}
	}
}
