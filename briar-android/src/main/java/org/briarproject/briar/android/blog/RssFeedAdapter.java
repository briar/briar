package org.briarproject.briar.android.blog;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.api.feed.Feed;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

class RssFeedAdapter extends BriarAdapter<Feed, RssFeedAdapter.FeedViewHolder> {

	private final RssFeedListener listener;

	RssFeedAdapter(Context ctx, RssFeedListener listener) {
		super(ctx, Feed.class);
		this.listener = listener;
	}

	@Override
	public FeedViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_rss_feed, parent, false);
		return new FeedViewHolder(v);
	}

	@Override
	public void onBindViewHolder(FeedViewHolder ui, int position) {
		final Feed item = getItemAt(position);
		if (item == null) return;

		// Feed Title
		ui.title.setText(item.getTitle());

		// Delete Button
		ui.delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onDeleteClick(item);
			}
		});

		// Author
		if (item.getAuthor() != null) {
			ui.author.setText(item.getAuthor());
			ui.author.setVisibility(VISIBLE);
			ui.authorLabel.setVisibility(VISIBLE);
		} else {
			ui.author.setVisibility(GONE);
			ui.authorLabel.setVisibility(GONE);
		}

		// Imported and Last Updated
		ui.imported.setText(UiUtils.formatDate(ctx, item.getAdded()));
		ui.updated.setText(UiUtils.formatDate(ctx, item.getUpdated()));

		// Description
		if (item.getDescription() != null) {
			ui.description.setText(item.getDescription());
			ui.description.setVisibility(VISIBLE);
		} else {
			ui.description.setVisibility(GONE);
		}

		// Open feed's blog when clicked
		ui.layout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onFeedClick(item);
			}
		});
	}

	@Override
	public int compare(Feed a, Feed b) {
		if (a == b) return 0;
		long aTime = a.getAdded(), bTime = b.getAdded();
		if (aTime > bTime) return -1;
		if (aTime < bTime) return 1;
		return 0;
	}

	@Override
	public boolean areContentsTheSame(Feed a, Feed b) {
		return a.getUpdated() == b.getUpdated();
	}

	@Override
	public boolean areItemsTheSame(Feed a, Feed b) {
		return a.getUrl().equals(b.getUrl()) &&
				a.getBlogId().equals(b.getBlogId()) &&
				a.getAdded() == b.getAdded();
	}

	static class FeedViewHolder extends RecyclerView.ViewHolder {
		private final View layout;
		private final TextView title;
		private final ImageButton delete;
		private final TextView imported;
		private final TextView updated;
		private final TextView author;
		private final TextView authorLabel;
		private final TextView description;

		private FeedViewHolder(View v) {
			super(v);

			layout = v;
			title = (TextView) v.findViewById(R.id.titleView);
			delete = (ImageButton) v.findViewById(R.id.deleteButton);
			imported = (TextView) v.findViewById(R.id.importedView);
			updated = (TextView) v.findViewById(R.id.updatedView);
			author = (TextView) v.findViewById(R.id.authorView);
			authorLabel = (TextView) v.findViewById(R.id.author);
			description = (TextView) v.findViewById(R.id.descriptionView);
		}
	}

	interface RssFeedListener {
		void onFeedClick(Feed feed);
		void onDeleteClick(Feed feed);
	}

}
