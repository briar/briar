package org.briarproject.bramble.system;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import org.briarproject.bramble.api.system.AndroidExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

class AndroidExecutorImpl implements AndroidExecutor {

	private final Handler uiHandler;
	private final Runnable loop;
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final CountDownLatch startLatch = new CountDownLatch(1);

	private volatile Handler backgroundHandler = null;

	@Inject
	AndroidExecutorImpl(Application app) {
		uiHandler = new Handler(app.getApplicationContext().getMainLooper());
		loop = new Runnable() {
			@Override
			public void run() {
				Looper.prepare();
				backgroundHandler = new Handler();
				startLatch.countDown();
				Looper.loop();
			}
		};
	}

	private void startIfNecessary() {
		if (!started.getAndSet(true)) {
			Thread t = new Thread(loop, "AndroidExecutor");
			t.setDaemon(true);
			t.start();
		}
		try {
			startLatch.await();
		} catch (InterruptedException e) {
			throw new RejectedExecutionException(e);
		}
	}

	@Override
	public <V> Future<V> runOnBackgroundThread(Callable<V> c) {
		FutureTask<V> f = new FutureTask<>(c);
		runOnBackgroundThread(f);
		return f;
	}

	@Override
	public void runOnBackgroundThread(Runnable r) {
		startIfNecessary();
		backgroundHandler.post(r);
	}

	@Override
	public <V> Future<V> runOnUiThread(Callable<V> c) {
		FutureTask<V> f = new FutureTask<>(c);
		runOnUiThread(f);
		return f;
	}

	@Override
	public void runOnUiThread(Runnable r) {
		uiHandler.post(r);
	}
}
