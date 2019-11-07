package org.briarproject.briar.android.threaded;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.ItemReturningAdapter;
import org.briarproject.briar.android.util.VersionedAdapter;

import java.util.Collection;

import javax.annotation.Nullable;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

@UiThread
public class ThreadItemAdapter<I extends ThreadItem>
		extends RecyclerView.Adapter<BaseThreadItemViewHolder<I>>
		implements VersionedAdapter, ItemReturningAdapter<I> {

	static final int UNDEFINED = -1;

	protected final NestedTreeList<I> items = new NestedTreeList<>();
	private final ThreadItemListener<I> listener;
	private final LinearLayoutManager layoutManager;

	private volatile int revision = 0;

	public ThreadItemAdapter(ThreadItemListener<I> listener,
			LinearLayoutManager layoutManager) {
		this.listener = listener;
		this.layoutManager = layoutManager;
	}

	@NonNull
	@Override
	public BaseThreadItemViewHolder<I> onCreateViewHolder(@NonNull
			ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.list_item_thread, parent, false);
		return new ThreadPostViewHolder<>(v);
	}

	@Override
	public void onBindViewHolder(@NonNull BaseThreadItemViewHolder<I> ui,
			int position) {
		I item = items.get(position);
		ui.bind(item, listener);
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	@Override
	public int getRevision() {
		return revision;
	}

	@Override
	public void incrementRevision() {
		revision++;
	}

	void setItemWithIdVisible(MessageId messageId) {
		int pos = 0;
		for (I item : items) {
			if (item.getId().equals(messageId)) {
				layoutManager.scrollToPosition(pos);
				break;
			}
			pos++;
		}
	}

	public void setItems(Collection<I> items) {
		this.items.clear();
		this.items.addAll(items);
		notifyDataSetChanged();
	}

	public void add(I item) {
		items.add(item);
		notifyItemInserted(findItemPosition(item));
	}

	@Nullable
	public I getItemAt(int position) {
		if (position == NO_POSITION || position >= items.size()) {
			return null;
		}
		return items.get(position);
	}

	protected int findItemPosition(@Nullable I item) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).equals(item)) return i;
		}
		return NO_POSITION; // Not found
	}

	boolean contains(MessageId m) {
		return items.contains(m);
	}

	/**
	 * Highlights the item with the given {@link MessageId}
	 * and disables the highlight for a previously highlighted item, if any.
	 *
	 * Only one item can be highlighted at a time.
	 */
	void setHighlightedItem(@Nullable MessageId id) {
		for (int i = 0; i < items.size(); i++) {
			I item = items.get(i);
			if (id != null && item.getId().equals(id)) {
				item.setHighlighted(true);
				notifyItemChanged(i, item);
			} else if (item.isHighlighted()) {
				item.setHighlighted(false);
				notifyItemChanged(i, item);
			}
		}
	}

	@Nullable
	I getHighlightedItem() {
		for (I i : items) {
			if (i.isHighlighted()) return i;
		}
		return null;
	}

	/**
	 * Returns the position of the first unread item below the current viewport
	 */
	int getVisibleUnreadPosBottom() {
		int positionBottom = layoutManager.findLastVisibleItemPosition();
		if (positionBottom == NO_POSITION) return NO_POSITION;
		for (int i = positionBottom + 1; i < items.size(); i++) {
			if (!items.get(i).isRead()) return i;
		}
		return NO_POSITION;
	}

	/**
	 * Returns the position of the first unread item above the current viewport
	 */
	int getVisibleUnreadPosTop() {
		int positionTop = layoutManager.findFirstVisibleItemPosition();
		int position = NO_POSITION;
		for (int i = 0; i < items.size(); i++) {
			if (i < positionTop && !items.get(i).isRead()) {
				position = i;
			} else if (i >= positionTop) {
				return position;
			}
		}
		return NO_POSITION;
	}

	public interface ThreadItemListener<I> {
		void onReplyClick(I item);
	}

}
