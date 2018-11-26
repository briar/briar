package org.briarproject.briar.android.conversation;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.support.media.ExifInterface;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static android.support.media.ExifInterface.ORIENTATION_ROTATE_270;
import static android.support.media.ExifInterface.ORIENTATION_ROTATE_90;
import static android.support.media.ExifInterface.ORIENTATION_TRANSPOSE;
import static android.support.media.ExifInterface.ORIENTATION_TRANSVERSE;
import static android.support.media.ExifInterface.TAG_IMAGE_LENGTH;
import static android.support.media.ExifInterface.TAG_IMAGE_WIDTH;
import static android.support.media.ExifInterface.TAG_ORIENTATION;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@NotNullByDefault
class AttachmentController {

	private static final Logger LOG =
			getLogger(AttachmentController.class.getName());

	private final MessagingManager messagingManager;
	private final int defaultSize;
	private final int minWidth, maxWidth;
	private final int minHeight, maxHeight;

	private final Map<MessageId, List<AttachmentItem>> attachmentCache =
			new ConcurrentHashMap<>();

	AttachmentController(MessagingManager messagingManager, Resources res) {
		this.messagingManager = messagingManager;
		defaultSize =
				res.getDimensionPixelSize(R.dimen.message_bubble_image_default);
		minWidth = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_min_width);
		maxWidth = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_max_width);
		minHeight = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_min_height);
		maxHeight = res.getDimensionPixelSize(
				R.dimen.message_bubble_image_max_height);
	}

	void put(MessageId messageId, List<AttachmentItem> attachments) {
		attachmentCache.put(messageId, attachments);
	}

	@Nullable
	List<AttachmentItem> get(MessageId messageId) {
		return attachmentCache.get(messageId);
	}

	@DatabaseExecutor
	List<Pair<AttachmentHeader, Attachment>> getMessageAttachments(
			List<AttachmentHeader> headers) throws DbException {
		long start = now();
		List<Pair<AttachmentHeader, Attachment>> attachments =
				new ArrayList<>(headers.size());
		for (AttachmentHeader h : headers) {
			Attachment a =
					messagingManager.getAttachment(h.getMessageId());
			attachments.add(new Pair<>(h, a));
		}
		logDuration(LOG, "Loading attachment", start);
		return attachments;
	}

	List<AttachmentItem> getAttachmentItems(
			List<Pair<AttachmentHeader, Attachment>> attachments) {
		List<AttachmentItem> items = new ArrayList<>(attachments.size());
		for (Pair<AttachmentHeader, Attachment> a : attachments) {
			AttachmentItem item =
					getAttachmentItem(a.getFirst(), a.getSecond());
			items.add(item);
		}
		return items;
	}

	private AttachmentItem getAttachmentItem(AttachmentHeader h, Attachment a) {
		MessageId messageId = h.getMessageId();
		Size size = new Size();

		InputStream is = a.getStream();
		is.mark(Integer.MAX_VALUE);
		try {
			// use exif to get size
			if (h.getContentType().equals("image/jpeg")) {
				size = getSizeFromExif(is);
			}
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
		try {
			// use BitmapFactory to get size
			if (size.error) {
				is.reset();
				size = getSizeFromBitmap(is);
			}
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				logException(LOG, WARNING, e);
			}
		}

		// calculate thumbnail size
		Size thumbnailSize = new Size(defaultSize, defaultSize);
		if (!size.error) {
			thumbnailSize = getThumbnailSize(size.width, size.height);
		}
		return new AttachmentItem(messageId, size.width, size.height,
				thumbnailSize.width, thumbnailSize.height, size.error);
	}

	/**
	 * Gets the size of a JPEG {@link InputStream} if EXIF info is available.
	 */
	private static Size getSizeFromExif(InputStream is)
			throws IOException {
		ExifInterface exif = new ExifInterface(is);
		// these can return 0 independent of default value
		int width = exif.getAttributeInt(TAG_IMAGE_WIDTH, 0);
		int height = exif.getAttributeInt(TAG_IMAGE_LENGTH, 0);
		if (width == 0 || height == 0) return new Size();
		int orientation = exif.getAttributeInt(TAG_ORIENTATION, 0);
		if (orientation == ORIENTATION_ROTATE_90 ||
				orientation == ORIENTATION_ROTATE_270 ||
				orientation == ORIENTATION_TRANSVERSE ||
				orientation == ORIENTATION_TRANSPOSE) {
			//noinspection SuspiciousNameCombination
			return new Size(height, width);
		}
		return new Size(width, height);
	}

	/**
	 * Gets the size of any image {@link InputStream}.
	 */
	private static Size getSizeFromBitmap(InputStream is) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(is, null, options);
		if (options.outWidth < 1 || options.outHeight < 1)
			return new Size();
		return new Size(options.outWidth, options.outHeight);
	}

	private Size getThumbnailSize(int width, int height) {
		float widthPercentage = maxWidth / (float) width;
		float heightPercentage = maxHeight / (float) height;
		float scaleFactor = Math.min(widthPercentage, heightPercentage);
		if (scaleFactor > 1) scaleFactor = 1f;
		int thumbnailWidth = (int) (width * scaleFactor);
		int thumbnailHeight = (int) (height * scaleFactor);
		if (thumbnailWidth < minWidth || thumbnailHeight < minHeight) {
			widthPercentage = minWidth / (float) width;
			heightPercentage = minHeight / (float) height;
			scaleFactor = Math.max(widthPercentage, heightPercentage);
			thumbnailWidth = (int) (width * scaleFactor);
			thumbnailHeight = (int) (height * scaleFactor);
			if (thumbnailWidth > maxWidth) thumbnailWidth = maxWidth;
			if (thumbnailHeight > maxHeight) thumbnailHeight = maxHeight;
		}
		return new Size(thumbnailWidth, thumbnailHeight);
	}

	private static class Size {

		private final int width;
		private final int height;
		private final boolean error;

		private Size(int width, int height) {
			this.width = width;
			this.height = height;
			this.error = false;
		}

		private Size() {
			this.width = 0;
			this.height = 0;
			this.error = true;
		}
	}

}
