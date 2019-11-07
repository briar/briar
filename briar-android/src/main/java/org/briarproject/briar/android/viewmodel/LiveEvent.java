package org.briarproject.briar.android.viewmodel;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

@NotNullByDefault
public class LiveEvent<T> extends LiveData<LiveEvent.ConsumableEvent<T>> {

	public void observeEvent(LifecycleOwner owner,
			LiveEventHandler<T> handler) {
		LiveEventObserver<T> observer = new LiveEventObserver<>(handler);
		super.observe(owner, observer);
	}

	static class ConsumableEvent<T> {

		private final T content;
		private boolean consumed = false;

		ConsumableEvent(T content) {
			this.content = content;
		}

		@Nullable
		T getContentIfNotConsumed() {
			if (consumed) return null;
			consumed = true;
			return content;
		}
	}

	@Immutable
	static class LiveEventObserver<T>
			implements Observer<ConsumableEvent<T>> {

		private final LiveEventHandler<T> handler;

		LiveEventObserver(LiveEventHandler<T> handler) {
			this.handler = handler;
		}

		@Override
		public void onChanged(@Nullable ConsumableEvent<T> consumableEvent) {
			if (consumableEvent != null) {
				T content = consumableEvent.getContentIfNotConsumed();
				if (content != null) handler.onEvent(content);
			}
		}

	}

	public interface LiveEventHandler<T> {
		void onEvent(T t);
	}
}
