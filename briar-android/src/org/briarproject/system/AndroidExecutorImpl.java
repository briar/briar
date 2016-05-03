package org.briarproject.system;

import android.os.Handler;
import android.os.Looper;

import org.briarproject.android.api.AndroidExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

class AndroidExecutorImpl implements AndroidExecutor {

	private final Runnable loop;
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final CountDownLatch startLatch = new CountDownLatch(1);

	private volatile Handler handler = null;

	@Inject
	AndroidExecutorImpl() {
		loop = new Runnable() {
			public void run() {
				Looper.prepare();
				handler = new Handler();
				startLatch.countDown();
				Looper.loop();
			}
		};
	}

	private void startIfNecessary() {
		if (started.getAndSet(true)) return;
		Thread t = new Thread(loop, "AndroidExecutor");
		t.setDaemon(true);
		t.start();
		try {
			startLatch.await();
		} catch (InterruptedException e) {
			throw new RejectedExecutionException(e);
		}
	}

	public <V> Future<V> submit(Callable<V> c) {
		FutureTask<V> f = new FutureTask<>(c);
		execute(f);
		return f;
	}

	public void execute(Runnable r) {
		startIfNecessary();
		handler.post(r);
	}
}
