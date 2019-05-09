package org.briarproject.briar.android.viewmodel;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class MutableLiveEvent<T> extends LiveEvent<T> {

	public void postEvent(T value) {
		super.postValue(new ConsumableEvent<>(value));
	}

	public void setEvent(T value) {
		super.setValue(new ConsumableEvent<>(value));
	}
}
