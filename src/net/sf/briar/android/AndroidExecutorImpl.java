package net.sf.briar.android;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import net.sf.briar.api.android.AndroidExecutor;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.inject.Inject;

class AndroidExecutorImpl implements AndroidExecutor {

	private static final int SHUTDOWN = 0, RUNNABLE = 1, CALLABLE = 2;

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
		new Thread(loop).start();
		try {
			startLatch.await();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public Future<Void> submit(Runnable r) {
		startIfNecessary();
		Future<Void> f = new FutureTask<Void>(r, null);
		Message m = Message.obtain(handler, RUNNABLE, f);
		handler.sendMessage(m);
		return f;
	}

	public <V> Future<V> submit(Callable<V> c) {
		startIfNecessary();
		Future<V> f = new FutureTask<V>(c);
		Message m = Message.obtain(handler, RUNNABLE, f);
		handler.sendMessage(m);
		return f;
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
			case RUNNABLE:
			case CALLABLE:
				((FutureTask<?>) m.obj).run();
				break;
			default:
				throw new IllegalArgumentException();
			}
		}
	}
}

