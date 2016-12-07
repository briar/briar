package org.briarproject.briar.android.controller.handler;

import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.UiThread;

public abstract class NewUiResultHandler<R> implements ResultHandler<R> {

	private static final int MESSAGE_POST_RESULT = 1;
	private static UiThreadHandler uiHandler;

	// Ui thread handler singleton, used for all result handlers
	private static Handler getHandler() {
		synchronized (NewUiResultHandler.class) {
			if (uiHandler == null) {
				uiHandler = new UiThreadHandler();
			}
			return uiHandler;
		}
	}

	private static class UiThreadHandler extends Handler {

		UiThreadHandler() {
			super(Looper.getMainLooper());
		}

		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MESSAGE_POST_RESULT) {
				ResultHandlerResult<?> mhr = (ResultHandlerResult<?>) msg.obj;
				mhr.resultHandler.onResultUi(mhr.result);
			}
		}
	}

	private static class ResultHandlerResult<R> {
		final NewUiResultHandler resultHandler;
		final R result;

		ResultHandlerResult(NewUiResultHandler resultHandler, R result) {
			this.resultHandler = resultHandler;
			this.result = result;
		}
	}

	@Override
	public void onResult(R result) {
		Binder.flushPendingCommands();
		Message message = getHandler().obtainMessage(MESSAGE_POST_RESULT,
				new ResultHandlerResult<>(this, result));
		message.sendToTarget();
	}

	@UiThread
	public abstract void onResultUi(R result);
}
