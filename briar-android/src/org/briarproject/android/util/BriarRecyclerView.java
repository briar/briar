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

		initViews();
	}

	public BriarRecyclerView(Context context, AttributeSet attrs) {
		super(context, attrs);

		initViews();
	}

	public BriarRecyclerView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);

		initViews();
	}

	private void initViews() {
		if (isInEditMode()) {
			return;
		}

		View v = LayoutInflater.from(getContext()).inflate(
				R.layout.briar_recycler_view, this, true);

		recyclerView = (RecyclerView) v.findViewById(R.id.recyclerView);
		emptyView = (TextView) v.findViewById(R.id.emptyView);
		progressBar = (ProgressBar) v.findViewById(R.id.progressBar);

		showProgressBar();

		// scroll down when opening keyboard
		if (Build.VERSION.SDK_INT >= 11) {
			recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v,
						int left, int top, int right, int bottom,
						int oldLeft, int oldTop, int oldRight, int oldBottom) {
					if (bottom < oldBottom) {
						recyclerView.postDelayed(new Runnable() {
							@Override
							public void run() {
								scrollToPosition(
										recyclerView.getAdapter().getItemCount() - 1);
							}
						}, 100);
					}
				}
			});
		}

		emptyObserver = new RecyclerView.AdapterDataObserver() {
			@Override
			public void onChanged() {
				showData();
			}

			@Override
			public void onItemRangeInserted(int positionStart, int itemCount) {
				super.onItemRangeInserted(positionStart, itemCount);
				onChanged();
			}
		};
	}

	public void setLayoutManager(RecyclerView.LayoutManager layout) {
		recyclerView.setLayoutManager(layout);
	}

	public void setAdapter(RecyclerView.Adapter adapter) {
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
		emptyView.setText(text);
	}

	public void showProgressBar() {
		recyclerView.setVisibility(View.INVISIBLE);
		emptyView.setVisibility(View.INVISIBLE);
		progressBar.setVisibility(View.VISIBLE);
	}

	public void showData() {
		RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
		if (adapter != null) {
			if (adapter.getItemCount() == 0) {
				emptyView.setVisibility(View.VISIBLE);
				recyclerView.setVisibility(View.INVISIBLE);
			} else {
				emptyView.setVisibility(View.INVISIBLE);
				recyclerView.setVisibility(View.VISIBLE);
			}
			progressBar.setVisibility(View.INVISIBLE);
		}
	}

	public void scrollToPosition(int position) {
		recyclerView.scrollToPosition(position);
	}

	public RecyclerView getRecyclerView() {
		return recyclerView;
	}

}
