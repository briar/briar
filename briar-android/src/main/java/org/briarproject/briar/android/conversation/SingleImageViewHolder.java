package org.briarproject.briar.android.conversation;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.conversation.glide.BriarImageTransformation;

import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.view.ViewCompat.LAYOUT_DIRECTION_RTL;

@NotNullByDefault
class SingleImageViewHolder extends ImageViewHolder {

	private final int radiusBig, radiusSmall;
	private final boolean isRtl;

	public SingleImageViewHolder(View v) {
		super(v);
		radiusBig = v.getContext().getResources()
				.getDimensionPixelSize(R.dimen.message_bubble_radius_big);
		radiusSmall = v.getContext().getResources()
				.getDimensionPixelSize(R.dimen.message_bubble_radius_small);

		// find out if we are showing a RTL language, Use the configuration,
		// because getting the layout direction of views is not reliable
		Configuration config = v.getContext().getResources().getConfiguration();
		isRtl = SDK_INT >= 17 &&
				config.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
	}

	void bind(AttachmentItem a, boolean isIncoming, boolean hasText) {
		if (!a.hasError()) beforeLoadingImage(a, isIncoming, hasText);
		super.bind(a);
	}

	private void beforeLoadingImage(AttachmentItem a, boolean isIncoming,
			boolean hasText) {
		// apply image size constraints, so glides picks them up for scaling
		LayoutParams layoutParams =
				new LayoutParams(a.getThumbnailWidth(), a.getThumbnailHeight());
		imageView.setLayoutParams(layoutParams);

		boolean leftCornerSmall =
				(isIncoming && !isRtl) || (!isIncoming && isRtl);
		boolean bottomRound = !hasText;
		transformation = new BriarImageTransformation(radiusSmall, radiusBig,
				leftCornerSmall, bottomRound);
	}

}
