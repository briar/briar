package org.briarproject.briar.android.view;

import android.net.Uri;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@NotNullByDefault
class ImagePreviewItem {

	private final Uri uri;
	private boolean waitForLoading = true;

	ImagePreviewItem(Uri uri) {
		this.uri = uri;
	}

	Uri getUri() {
		return uri;
	}

	void setWaitForLoading(boolean waitForLoading) {
		this.waitForLoading = waitForLoading;
	}

	boolean waitForLoading() {
		return waitForLoading;
	}

	static List<ImagePreviewItem> fromUris(Collection<Uri> uris) {
		List<ImagePreviewItem> items = new ArrayList<>(uris.size());
		for (Uri uri : uris) {
			items.add(new ImagePreviewItem(uri));
		}
		return items;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		return o instanceof ImagePreviewItem &&
				uri.equals(((ImagePreviewItem) o).uri);
	}

	@Override
	public int hashCode() {
		return uri.hashCode();
	}

}
