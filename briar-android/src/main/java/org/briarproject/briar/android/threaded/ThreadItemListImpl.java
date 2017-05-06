package org.briarproject.briar.android.threaded;

import org.briarproject.bramble.api.sync.MessageId;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class ThreadItemListImpl<I extends ThreadItem> extends ArrayList<I>
		implements ThreadItemList<I> {

	private MessageId bottomVisibleItemId;

	@Override
	public MessageId getFirstVisibleItemId() {
		return bottomVisibleItemId;
	}

	public void setFirstVisibleId(@Nullable MessageId bottomVisibleItemId) {
		this.bottomVisibleItemId = bottomVisibleItemId;
	}
}
