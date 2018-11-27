package org.briarproject.briar.android.conversation;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.annotation.UiThread;
import android.support.constraint.ConstraintSet;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.Transformation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.conversation.glide.BriarImageTransformation;
import org.briarproject.briar.android.conversation.glide.GlideApp;

import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.view.ViewCompat.LAYOUT_DIRECTION_RTL;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

@UiThread
@NotNullByDefault
class ConversationMessageViewHolder extends ConversationItemViewHolder {

	@DrawableRes
	private static final int ERROR_RES = R.drawable.ic_image_broken;

	private final ImageView imageView;
	private final ViewGroup statusLayout;
	private final int timeColor, timeColorBubble;
	private final int radiusBig, radiusSmall;
	private final boolean isRtl;
	private final ConstraintSet textConstraints = new ConstraintSet();
	private final ConstraintSet imageConstraints = new ConstraintSet();
	private final ConstraintSet imageTextConstraints = new ConstraintSet();

	ConversationMessageViewHolder(View v, boolean isIncoming) {
		super(v, isIncoming);
		imageView = v.findViewById(R.id.imageView);
		statusLayout = v.findViewById(R.id.statusLayout);
		radiusBig = v.getContext().getResources()
				.getDimensionPixelSize(R.dimen.message_bubble_radius_big);
		radiusSmall = v.getContext().getResources()
				.getDimensionPixelSize(R.dimen.message_bubble_radius_small);

		// remember original status text color
		timeColor = time.getCurrentTextColor();
		timeColorBubble =
				ContextCompat.getColor(v.getContext(), R.color.briar_white);

		// find out if we are showing a RTL language, Use the configuration,
		// because getting the layout direction of views is not reliable
		Configuration config =
				imageView.getContext().getResources().getConfiguration();
		isRtl = SDK_INT >= 17 &&
				config.getLayoutDirection() == LAYOUT_DIRECTION_RTL;

		// clone constraint sets from layout files
		textConstraints
				.clone(v.getContext(), R.layout.list_item_conversation_msg_in);
		imageConstraints.clone(v.getContext(),
				R.layout.list_item_conversation_msg_image);
		imageTextConstraints.clone(v.getContext(),
				R.layout.list_item_conversation_msg_image_text);

		// in/out are different layouts, so we need to do this only once
		textConstraints
				.setHorizontalBias(R.id.statusLayout, isIncoming() ? 1 : 0);
		imageConstraints
				.setHorizontalBias(R.id.statusLayout, isIncoming() ? 1 : 0);
		imageTextConstraints
				.setHorizontalBias(R.id.statusLayout, isIncoming() ? 1 : 0);
	}

	@Override
	void bind(ConversationItem conversationItem,
			ConversationListener listener) {
		super.bind(conversationItem, listener);
		ConversationMessageItem item =
				(ConversationMessageItem) conversationItem;
		if (item.getAttachments().isEmpty()) {
			bindTextItem();
		} else {
			bindImageItem(item);
		}
	}

	private void bindTextItem() {
		clearImage();
		statusLayout.setBackgroundResource(0);
		// also reset padding (the background drawable defines some)
		statusLayout.setPadding(0, 0, 0, 0);
		time.setTextColor(timeColor);
		textConstraints.applyTo(layout);
	}

	private void bindImageItem(ConversationMessageItem item) {
		// TODO show more than just the first image
		AttachmentItem attachment = item.getAttachments().get(0);

		ConstraintSet constraintSet;
		if (item.getText() == null) {
			statusLayout
					.setBackgroundResource(R.drawable.msg_status_bubble);
			time.setTextColor(timeColorBubble);
			constraintSet = imageConstraints;
		} else {
			statusLayout.setBackgroundResource(0);
			// also reset padding (the background drawable defines some)
			statusLayout.setPadding(0, 0, 0, 0);
			time.setTextColor(timeColor);
			constraintSet = imageTextConstraints;
		}

		// apply image size constraints, so glides picks them up for scaling
		int width = attachment.getThumbnailWidth();
		int height = attachment.getThumbnailHeight();
		constraintSet.constrainWidth(R.id.imageView, width);
		constraintSet.constrainHeight(R.id.imageView, height);
		constraintSet.applyTo(layout);

		if (attachment.hasError()) {
			clearImage();
			imageView.setImageResource(ERROR_RES);
		} else {
			loadImage(item, attachment);
		}
	}

	private void clearImage() {
		GlideApp.with(imageView)
				.clear(imageView);
	}

	private void loadImage(ConversationMessageItem item,
			AttachmentItem attachment) {
		boolean leftCornerSmall =
				(isIncoming() && !isRtl) || (!isIncoming() && isRtl);
		boolean bottomRound = item.getText() == null;
		Transformation<Bitmap> transformation = new BriarImageTransformation(
				radiusSmall, radiusBig, leftCornerSmall, bottomRound);

		GlideApp.with(imageView)
				.load(attachment)
				.diskCacheStrategy(NONE)
				.error(ERROR_RES)
				.transform(transformation)
				.transition(withCrossFade())
				.into(imageView)
				.waitForLayout();
	}

}
