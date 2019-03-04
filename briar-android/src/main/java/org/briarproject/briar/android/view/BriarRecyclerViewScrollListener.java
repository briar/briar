package org.briarproject.briar.android.view;

import android.support.annotation.CallSuper;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.util.ItemReturningAdapter;

import static android.support.v7.widget.RecyclerView.NO_POSITION;
import static java.util.Objects.requireNonNull;

@NotNullByDefault
public abstract class BriarRecyclerViewScrollListener<A extends ItemReturningAdapter<I>, I>
		extends OnScrollListener {

	protected final A adapter;

	private int prevFirstVisible = NO_POSITION;
	private int prevLastVisible = NO_POSITION;
	private int prevItemCount = 0;

	protected BriarRecyclerViewScrollListener(A adapter) {
		this.adapter = adapter;
	}

	@Override
	public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
		LinearLayoutManager layoutManager = (LinearLayoutManager)
				requireNonNull(recyclerView.getLayoutManager());
		int firstVisible = layoutManager.findFirstVisibleItemPosition();
		int lastVisible = layoutManager.findLastVisibleItemPosition();
		int itemCount = adapter.getItemCount();

		if (firstVisible != prevFirstVisible ||
				lastVisible != prevLastVisible ||
				itemCount != prevItemCount) {
			onNewItemVisible(firstVisible, lastVisible, itemCount);
			prevFirstVisible = firstVisible;
			prevLastVisible = lastVisible;
			prevItemCount = itemCount;
		}
	}

	@CallSuper
	protected void onNewItemVisible(int firstVisible, int lastVisible,
			int itemCount) {
		boolean init = prevFirstVisible == NO_POSITION ||
				prevLastVisible == NO_POSITION;
		int visibleItems = prevLastVisible - prevFirstVisible;
		boolean jump = Math.abs(firstVisible - prevFirstVisible) > visibleItems;

		if (init || jump) {
			// If the list has loaded, or we jump scrolled,
			// all visible items are newly visible
			for (int i = firstVisible; i <= lastVisible; i++)
				onNewItemVisible(i);
			return;
		}

		boolean up = firstVisible > prevFirstVisible ||
				lastVisible > prevLastVisible;
		if (up) {
			for (int i = prevLastVisible + 1; i <= lastVisible; i++)
				onNewItemVisible(i);
		} else {
			for (int i = firstVisible; i < prevFirstVisible; i++)
				onNewItemVisible(i);
		}
	}

	private void onNewItemVisible(int position) {
		I item = requireNonNull(adapter.getItemAt(position));
		onNewItemVisible(item);
	}

	protected abstract void onNewItemVisible(I item);

}
