package org.briarproject.briar.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.briar.R;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import static org.briarproject.briar.android.util.UiUtils.MIN_DATE_RESOLUTION;

public class BriarRecyclerView extends FrameLayout {

	private static final Logger LOG =
			Logger.getLogger(BriarRecyclerView.class.getName());

	private final Handler handler = new Handler(Looper.getMainLooper());

	private RecyclerView recyclerView;
	private TextView emptyView;
	private ProgressBar progressBar;
	private RecyclerView.AdapterDataObserver emptyObserver;
	private Runnable refresher = null;
	private boolean isScrollingToEnd = false;

	public BriarRecyclerView(Context context) {
		this(context, null, 0);
	}

	public BriarRecyclerView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public BriarRecyclerView(Context context, @Nullable AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);

		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.BriarRecyclerView);
		isScrollingToEnd = attributes
				.getBoolean(R.styleable.BriarRecyclerView_scrollToEnd, true);
		String emtpyText =
				attributes.getString(R.styleable.BriarRecyclerView_emptyText);
		if (emtpyText != null) setEmptyText(emtpyText);
		attributes.recycle();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		stopPeriodicUpdate();
	}

	private void initViews() {
		View v = LayoutInflater.from(getContext()).inflate(
				R.layout.briar_recycler_view, this, true);

		recyclerView = (RecyclerView) v.findViewById(R.id.recyclerView);
		emptyView = (TextView) v.findViewById(R.id.emptyView);
		progressBar = (ProgressBar) v.findViewById(R.id.progressBar);

		showProgressBar();

		// scroll down when opening keyboard
		if (isScrollingToEnd) {
			addLayoutChangeListener();
		}

		emptyObserver = new RecyclerView.AdapterDataObserver() {
			@Override
			public void onItemRangeInserted(int positionStart, int itemCount) {
				super.onItemRangeInserted(positionStart, itemCount);
				if (itemCount > 0) showData();
			}

			@Override
			public void onItemRangeRemoved(int positionStart, int itemCount) {
				super.onItemRangeRemoved(positionStart, itemCount);
				if (itemCount > 0) showData();
			}
		};
	}

	private void addLayoutChangeListener() {
		recyclerView.addOnLayoutChangeListener(
				new OnLayoutChangeListener() {
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

	public void startPeriodicUpdate() {
		if (recyclerView == null || recyclerView.getAdapter() == null) {
			throw new IllegalStateException("Need to call setAdapter() first!");
		}
		refresher = new Runnable() {
			@Override
			public void run() {
				LOG.info("Updating Content...");
				Adapter adapter = recyclerView.getAdapter();
				adapter.notifyItemRangeChanged(0, adapter.getItemCount());
				handler.postDelayed(refresher, MIN_DATE_RESOLUTION);
			}
		};
		LOG.info("Adding Handler Callback");
		handler.postDelayed(refresher, MIN_DATE_RESOLUTION);
	}

	public void stopPeriodicUpdate() {
		if (refresher != null) {
			LOG.info("Removing Handler Callback");
			handler.removeCallbacks(refresher);
			refresher = null;
		}
	}

}
