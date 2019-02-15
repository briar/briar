package org.briarproject.briar.android.view;

import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.ImagePreview.ImagePreviewListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static android.support.v7.widget.RecyclerView.NO_POSITION;
import static java.util.Objects.requireNonNull;

@NotNullByDefault
class ImagePreviewAdapter extends Adapter<ImagePreviewViewHolder> {

	private final List<ImagePreviewItem> items;
	private final ImagePreviewListener listener;
	@LayoutRes
	private final int layout;

	ImagePreviewAdapter(Collection<ImagePreviewItem> items,
			ImagePreviewListener listener) {
		this.items = new ArrayList<>(items);
		this.listener = listener;
		this.layout = items.size() == 1 ?
				R.layout.list_item_image_preview_single :
				R.layout.list_item_image_preview;
	}

	@Override
	public ImagePreviewViewHolder onCreateViewHolder(ViewGroup viewGroup,
			int type) {
		View v = LayoutInflater.from(viewGroup.getContext())
				.inflate(layout, viewGroup, false);
		return new ImagePreviewViewHolder(v, requireNonNull(listener));
	}

	@Override
	public void onBindViewHolder(ImagePreviewViewHolder viewHolder,
			int position) {
		viewHolder.bind(items.get(position));
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	void loadItemPreview(ImagePreviewItem item) {
		int pos = items.indexOf(item);
		if (pos == NO_POSITION) throw new AssertionError();
		ImagePreviewItem newItem = items.get(pos);
		newItem.setWaitForLoading(false);
		notifyItemChanged(pos, newItem);
	}

}
