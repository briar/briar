package org.briarproject.briar.android.threaded;

import org.briarproject.bramble.api.sync.MessageId;

import java.util.List;

import javax.annotation.Nullable;

public interface ThreadItemList<I extends ThreadItem> extends List<I> {

	@Nullable
	MessageId getBottomVisibleItemId();

	void setBottomVisibleItemId(@Nullable MessageId bottomVisibleItemId);
}
