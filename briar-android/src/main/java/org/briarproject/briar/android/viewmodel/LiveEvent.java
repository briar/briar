package org.briarproject.briar.android.viewmodel;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@NotNullByDefault
public class LiveEvent<T> extends LiveData<LiveEvent.ConsumableEvent<T>> {

	public void observeEvent(LifecycleOwner owner,
			LiveEventHandler<T> handler) {
		LiveEventObserver<T> observer = new LiveEventObserver<>(handler);
		super.observe(owner, observer);
	}

	public static class ConsumableEvent<T> {
		private final T content;
		private boolean consumed = false;

		public ConsumableEvent(T content) {
			this.content = content;
		}

		@Nullable
		public T getContentIfNotConsumed() {
			if (consumed) return null;
			else {
				consumed = true;
				return content;
			}
		}
	}

	@Immutable
	public static class LiveEventObserver<T>
			implements Observer<ConsumableEvent<T>> {
		private final LiveEventHandler<T> handler;

		public LiveEventObserver(LiveEventHandler<T> handler) {
			this.handler = handler;
		}

		@Override
		public void onChanged(@Nullable ConsumableEvent<T> consumableEvent) {
			if (consumableEvent != null) {
				T content = consumableEvent.getContentIfNotConsumed();
				if (content != null) handler.onEventUnconsumedContent(content);
			}
		}

	}

	public interface LiveEventHandler<T> {
		void onEventUnconsumedContent(T t);
	}
}
