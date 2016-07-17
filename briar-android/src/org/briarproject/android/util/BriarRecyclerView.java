package org.briarproject.android.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.R;

import java.util.logging.Logger;

import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

public class BriarRecyclerView extends FrameLayout {

	private RecyclerView recyclerView;
	private TextView emptyView;
	private ProgressBar progressBar;
	private RecyclerView.AdapterDataObserver emptyObserver;
	private Runnable refresher = null;
	private boolean isScrollingToEnd = false;

	private final Logger LOG = Logger.getLogger(getClass().getName());
	private final long DEFAULT_REFRESH_INTERVAL = MINUTE_IN_MILLIS;

	public BriarRecyclerView(Context context) {
		this(context, null, 0);
	}

	public BriarRecyclerView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public BriarRecyclerView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);

		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.BriarRecyclerView);
		isScrollingToEnd = attributes
				.getBoolean(R.styleable.BriarRecyclerView_scrollToEnd, true);
		attributes.recycle();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (refresher != null) {
			LOG.info("Removing Handler Callback");
			removeCallbacks(refresher);
		}
	}

	private void initViews() {
		View v = LayoutInflater.from(getContext()).inflate(
				R.layout.briar_recycler_view, this, true);

		recyclerView = (RecyclerView) v.findViewById(R.id.recyclerView);
		emptyView = (TextView) v.findViewById(R.id.emptyView);
		progressBar = (ProgressBar) v.findViewById(R.id.progressBar);

		showProgressBar();

		// scroll down when opening keyboard
		if (isScrollingToEnd && Build.VERSION.SDK_INT >= 11) {
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

	public void setAdapter(Adapter adapter) {
		if (recyclerView == null) initViews();

		Adapter oldAdapter = recyclerView.getAdapter();
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

	public void setEmptyText(int res) {
		if (recyclerView == null) initViews();
		emptyView.setText(res);
	}

	public void showProgressBar() {
		if (recyclerView == null) initViews();
		recyclerView.setVisibility(INVISIBLE);
		emptyView.setVisibility(INVISIBLE);
		progressBar.setVisibility(VISIBLE);
	}

	public void showData() {
		if (recyclerView == null) initViews();
		Adapter adapter = recyclerView.getAdapter();
		if (adapter != null) {
			if (adapter.getItemCount() == 0) {
				emptyView.setVisibility(VISIBLE);
				recyclerView.setVisibility(INVISIBLE);
			} else {
				// use GONE here so empty view doesn't use space on small lists
				emptyView.setVisibility(GONE);
				recyclerView.setVisibility(VISIBLE);
			}
			progressBar.setVisibility(GONE);
		}
	}

	public void scrollToPosition(int position) {
		if (recyclerView == null) initViews();
		recyclerView.scrollToPosition(position);
	}

	public void smoothScrollToPosition(int position) {
		if (recyclerView == null) initViews();
		recyclerView.smoothScrollToPosition(position);
	}

	public RecyclerView getRecyclerView() {
		return this.recyclerView;
	}

	public void periodicallyUpdateContent() {
		if (recyclerView == null || recyclerView.getAdapter() == null) {
			throw new IllegalStateException("Need to call setAdapter() first!");
		}
		refresher = new Runnable() {
			@Override
			public void run() {
				LOG.info("Updating Content...");
				recyclerView.getAdapter().notifyDataSetChanged();
				postDelayed(refresher, DEFAULT_REFRESH_INTERVAL);
			}
		};
		LOG.info("Adding Handler Callback");
		postDelayed(refresher, DEFAULT_REFRESH_INTERVAL);
	}

}
