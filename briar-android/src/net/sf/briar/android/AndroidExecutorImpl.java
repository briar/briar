package net.sf.briar.android;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import net.sf.briar.api.android.AndroidExecutor;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

class AndroidExecutorImpl implements AndroidExecutor {

	private static final int SHUTDOWN = 0, RUN = 1;

	private final Runnable loop;
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final CountDownLatch startLatch = new CountDownLatch(1);

	private volatile Handler handler = null;

	@Inject
	AndroidExecutorImpl() {
		loop = new Runnable() {
			public void run() {
				Looper.prepare();
				handler = new FutureTaskHandler();
				startLatch.countDown();
				Looper.loop();
			}
		};
	}

	private void startIfNecessary() {
		if(started.getAndSet(true)) return;
		new Thread(loop, "AndroidExecutor").start();
		try {
			startLatch.await();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public <V> V call(Callable<V> c) throws InterruptedException,
	ExecutionException {
		startIfNecessary();
		Future<V> f = new FutureTask<V>(c);
		Message m = Message.obtain(handler, RUN, f);
		handler.sendMessage(m);
		return f.get();
	}

	public void shutdown() {
		if(handler != null) {
			Message m = Message.obtain(handler, SHUTDOWN);
			handler.sendMessage(m);
		}
	}

	private static class FutureTaskHandler extends Handler {

		@Override
		public void handleMessage(Message m) {
			switch(m.what) {
			case SHUTDOWN:
				Looper.myLooper().quit();
				break;
			case RUN:
				((FutureTask<?>) m.obj).run();
				break;
			default:
				throw new IllegalArgumentException();
			}
		}
	}
}

