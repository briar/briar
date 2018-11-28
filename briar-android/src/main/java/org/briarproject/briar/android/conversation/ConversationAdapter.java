package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView.RecycledViewPool;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;

import javax.annotation.Nullable;

@NotNullByDefault
class ConversationAdapter
		extends BriarAdapter<ConversationItem, ConversationItemViewHolder> {

	private ConversationListener listener;
	private final RecycledViewPool imageViewPool;
	private final ImageItemDecoration imageItemDecoration;

	ConversationAdapter(Context ctx,
			ConversationListener conversationListener) {
		super(ctx, ConversationItem.class);
		listener = conversationListener;
		// This shares the same pool for view recycling between all image lists
		imageViewPool = new RecycledViewPool();
		// Share the item decoration as well
		imageItemDecoration = new ImageItemDecoration(ctx);
	}

	@LayoutRes
	@Override
	public int getItemViewType(int position) {
		ConversationItem item = items.get(position);
		return item.getLayout();
	}

	@Override
	public ConversationItemViewHolder onCreateViewHolder(ViewGroup viewGroup,
			@LayoutRes int type) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				type, viewGroup, false);
		switch (type) {
			case R.layout.list_item_conversation_msg_in:
				return new ConversationMessageViewHolder(v, listener, true,
						imageViewPool, imageItemDecoration);
			case R.layout.list_item_conversation_msg_out:
				return new ConversationMessageViewHolder(v, listener, false,
						imageViewPool, imageItemDecoration);
			case R.layout.list_item_conversation_notice_in:
				return new ConversationNoticeViewHolder(v, listener, true);
			case R.layout.list_item_conversation_notice_out:
				return new ConversationNoticeViewHolder(v, listener, false);
			case R.layout.list_item_conversation_request:
				return new ConversationRequestViewHolder(v, listener, true);
			default:
				throw new IllegalArgumentException("Unknown ConversationItem");
		}
	}

	@Override
	public void onBindViewHolder(ConversationItemViewHolder ui, int position) {
		ConversationItem item = items.get(position);
		ui.bind(item);
		listener.onItemVisible(item);
	}

	@Override
	public int compare(ConversationItem c1, ConversationItem c2) {
		return Long.compare(c1.getTime(), c2.getTime());
	}

	@Override
	public boolean areItemsTheSame(ConversationItem c1,
			ConversationItem c2) {
		return c1.getId().equals(c2.getId());
	}

	@Override
	public boolean areContentsTheSame(ConversationItem c1,
			ConversationItem c2) {
		return c1.equals(c2);
	}

	@Nullable
	ConversationItem getLastItem() {
		if (items.size() > 0) {
			return items.get(items.size() - 1);
		} else {
			return null;
		}
	}

	SparseArray<ConversationItem> getOutgoingMessages() {
		SparseArray<ConversationItem> messages = new SparseArray<>();

		for (int i = 0; i < items.size(); i++) {
			ConversationItem item = items.get(i);
			if (!item.isIncoming()) {
				messages.put(i, item);
			}
		}
		return messages;
	}

	@Nullable
	Pair<Integer, ConversationMessageItem> getMessageItem(MessageId messageId) {
		for (int i = 0; i < items.size(); i++) {
			ConversationItem item = items.get(i);
			if (item instanceof ConversationMessageItem &&
					item.getId().equals(messageId)) {
				return new Pair<>(i, (ConversationMessageItem) item);
			}
		}
		return null;
	}

	boolean isScrolledToBottom(LinearLayoutManager layoutManager) {
		return layoutManager.findLastVisibleItemPosition() == items.size() - 1;
	}

}
