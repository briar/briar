package org.briarproject.briar.android.conversation;

import android.content.res.Resources;
import android.support.annotation.VisibleForTesting;

import org.briarproject.briar.R;

class AttachmentDimensions {

	final int defaultSize;
	final int minWidth, maxWidth;
	final int minHeight, maxHeight;

	@VisibleForTesting
	AttachmentDimensions(int defaultSize, int minWidth, int maxWidth,
			int minHeight, int maxHeight) {
		this.defaultSize = defaultSize;
		this.minWidth = minWidth;
		this.maxWidth = maxWidth;
		this.minHeight = minHeight;
		this.maxHeight = maxHeight;
	}

	static AttachmentDimensions getAttachmentDimensions(Resources res) {
		int defaultSize =
				res.getDimensionPixelSize(R.dimen.message_bubble_image_default);
		int minWidth = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_min_width);
		int maxWidth = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_max_width);
		int minHeight = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_min_height);
		int maxHeight = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_max_height);
		return new AttachmentDimensions(defaultSize, minWidth, maxWidth,
				minHeight, minHeight);
	}

}
