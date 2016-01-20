package org.briarproject.android;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.briarproject.api.android.AndroidExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.inject.Inject;

class AndroidExecutorImpl implements AndroidExecutor {

	private final Handler handler;

	@Inject
	AndroidExecutorImpl(Application app) {
		Context ctx = app.getApplicationContext();
		handler = new FutureTaskHandler(ctx.getMainLooper());
	}

	public <V> Future<V> submit(Callable<V> c) {
		Future<V> f = new FutureTask<V>(c);
		handler.sendMessage(Message.obtain(handler, 0, f));
		return f;
	}

	public void execute(Runnable r) {
		handler.post(r);
	}

	private static class FutureTaskHandler extends Handler {

		private FutureTaskHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message m) {
			((FutureTask<?>) m.obj).run();
		}
	}
}

