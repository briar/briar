package org.briarproject.briar.android.conversation;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@NotNullByDefault
class ImageAdapter extends Adapter<ImageViewHolder> {

	private final static int TYPE_SINGLE = 0;
	private final static int TYPE_MULTIPLE = 1;

	private final List<AttachmentItem> items = new ArrayList<>();
	private final ConversationListener listener;
	@Nullable
	private ConversationMessageItem conversationItem;

	public ImageAdapter(ConversationListener listener) {
		super();
		this.listener = listener;
	}

	@Override
	public int getItemViewType(int position) {
		return items.size() == 1 ? TYPE_SINGLE : TYPE_MULTIPLE;
	}

	@Override
	public ImageViewHolder onCreateViewHolder(ViewGroup viewGroup, int type) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_image, viewGroup, false);
		return type == TYPE_SINGLE ? new SingleImageViewHolder(v) :
				new ImageViewHolder(v);
	}

	@Override
	public void onBindViewHolder(ImageViewHolder imageViewHolder,
			int position) {
		requireNonNull(conversationItem);
		AttachmentItem item = items.get(position);
		imageViewHolder.itemView.setOnClickListener(v ->
				listener.onAttachmentClicked(v, conversationItem, item)
		);
		if (imageViewHolder instanceof SingleImageViewHolder) {
			boolean isIncoming = conversationItem.isIncoming();
			boolean hasText = conversationItem.getText() != null;
			((SingleImageViewHolder) imageViewHolder)
					.bind(item, isIncoming, hasText);
		} else {
			imageViewHolder.bind(item);
		}
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	void setConversationItem(ConversationMessageItem item) {
		this.conversationItem = item;
		this.items.clear();
		this.items.addAll(item.getAttachments());
		notifyDataSetChanged();
	}

	void clear() {
		items.clear();
		notifyDataSetChanged();
	}

}
