package org.briarproject.android.util;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.R;

public class BriarRecyclerView extends FrameLayout {

	private RecyclerView recyclerView;
	private TextView emptyView;
	private ProgressBar progressBar;
	private RecyclerView.AdapterDataObserver emptyObserver;

	public BriarRecyclerView(Context context) {
		super(context);
	}

	public BriarRecyclerView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public BriarRecyclerView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
	}

	private void initViews() {

		View v = LayoutInflater.from(getContext()).inflate(
				R.layout.briar_recycler_view, this, true);

		recyclerView = (RecyclerView) v.findViewById(R.id.recyclerView);
		emptyView = (TextView) v.findViewById(R.id.emptyView);
		progressBar = (ProgressBar) v.findViewById(R.id.progressBar);

		showProgressBar();

		// scroll down when opening keyboard
		if (Build.VERSION.SDK_INT >= 11) {
			recyclerView.addOnLayoutChangeListener(
					new View.OnLayoutChangeListener() {
						@Override
						public void onLayoutChange(View v, int left, int top,
								int right, int bottom, int oldLeft, int oldTop,
								int oldRight, int oldBottom) {
							if (bottom < oldBottom) {
								recyclerView.postDelayed(new Runnable() {
									@Override
									public void run() {
										scrollToPosition(
												recyclerView.getAdapter()
														.getItemCount() - 1);
									}
								}, 100);
							}
						}
					});
		}

		emptyObserver = new RecyclerView.AdapterDataObserver() {
			@Override
			public void onItemRangeInserted(int positionStart, int itemCount) {
				super.onItemRangeInserted(positionStart, itemCount);
				if (itemCount > 0) showData();
			}
		};
	}

	public void setLayoutManager(RecyclerView.LayoutManager layout) {
		if (recyclerView == null) initViews();
		recyclerView.setLayoutManager(layout);
	}

	public void setAdapter(RecyclerView.Adapter adapter) {
		if (recyclerView == null) initViews();

		RecyclerView.Adapter oldAdapter = recyclerView.getAdapter();
		if (oldAdapter != null) {
			oldAdapter.unregisterAdapterDataObserver(emptyObserver);
		}

		recyclerView.setAdapter(adapter);

		if (adapter != null) {
			adapter.registerAdapterDataObserver(emptyObserver);

			if (adapter.getItemCount() > 0) {
				// only show data if adapter has data already
				// otherwise progress bar is shown
				emptyObserver.onChanged();
			}
		}
	}

	public void setEmptyText(String text) {
		if (recyclerView == null) initViews();
		emptyView.setText(text);
	}

	public void showProgressBar() {
		if (recyclerView == null) initViews();
		recyclerView.setVisibility(INVISIBLE);
		emptyView.setVisibility(INVISIBLE);
		progressBar.setVisibility(VISIBLE);
	}

	public void showData() {
		if (recyclerView == null) initViews();
		RecyclerView.Adapter adapter = recyclerView.getAdapter();
		if (adapter != null) {
			if (adapter.getItemCount() == 0) {
				emptyView.setVisibility(VISIBLE);
				recyclerView.setVisibility(INVISIBLE);
			} else {
				emptyView.setVisibility(INVISIBLE);
				recyclerView.setVisibility(VISIBLE);
			}
			progressBar.setVisibility(INVISIBLE);
		}
	}

	public void scrollToPosition(int position) {
		if (recyclerView == null) initViews();
		recyclerView.scrollToPosition(position);
	}

	public RecyclerView getRecyclerView() {
		return this.recyclerView;
	}

}
