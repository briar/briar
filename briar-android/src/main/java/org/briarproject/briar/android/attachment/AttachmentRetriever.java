package org.briarproject.briar.android.attachment;

import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.media.ExifInterface;
import android.webkit.MimeTypeMap;

import com.bumptech.glide.util.MarkEnforcingInputStream;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.attachment.ImageHelper.DecodeResult;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class AttachmentRetriever {

	private static final Logger LOG =
			getLogger(AttachmentRetriever.class.getName());
	private static final int READ_LIMIT = 1024 * 8192;

	private final MessagingManager messagingManager;
	private final ImageHelper imageHelper;
	private final int defaultSize;
	private final int minWidth, maxWidth;
	private final int minHeight, maxHeight;

	private final Map<MessageId, List<AttachmentItem>> attachmentCache =
			new ConcurrentHashMap<>();

	@VisibleForTesting
	AttachmentRetriever(MessagingManager messagingManager,
			AttachmentDimensions dimensions, ImageHelper imageHelper) {
		this.messagingManager = messagingManager;
		this.imageHelper = imageHelper;
		defaultSize = dimensions.defaultSize;
		minWidth = dimensions.minWidth;
		maxWidth = dimensions.maxWidth;
		minHeight = dimensions.minHeight;
		maxHeight = dimensions.maxHeight;
	}

	public AttachmentRetriever(MessagingManager messagingManager,
			AttachmentDimensions dimensions) {
		this(messagingManager, dimensions, new ImageHelper() {
			@Override
			public DecodeResult decodeStream(InputStream is) {
				Options options = new Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(is, null, options);
				String mimeType = options.outMimeType;
				if (mimeType == null) mimeType = "";
				return new DecodeResult(options.outWidth, options.outHeight,
						mimeType);
			}

			@Nullable
			@Override
			public String getExtensionFromMimeType(String mimeType) {
				MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
				return mimeTypeMap.getExtensionFromMimeType(mimeType);
			}
		});
	}

	public void cachePut(MessageId messageId,
			List<AttachmentItem> attachments) {
		attachmentCache.put(messageId, attachments);
	}

	@Nullable
	public List<AttachmentItem> cacheGet(MessageId messageId) {
		return attachmentCache.get(messageId);
	}

	public Attachment getMessageAttachment(AttachmentHeader h)
			throws DbException {
		return messagingManager.getAttachment(h);
	}

	/**
	 * Creates an {@link AttachmentItem} from the {@link Attachment}'s
	 * {@link InputStream} which will be closed when this method returns.
	 */
	public AttachmentItem getAttachmentItem(Attachment a, boolean needsSize) {
		AttachmentHeader h = a.getHeader();
		if (!needsSize) {
			String extension =
					imageHelper.getExtensionFromMimeType(h.getContentType());
			boolean hasError = false;
			if (extension == null) {
				extension = "";
				hasError = true;
			}
			return new AttachmentItem(h, 0, 0, extension, 0, 0, hasError);
		}

		Size size = new Size();
		InputStream is = new MarkEnforcingInputStream(
				new BufferedInputStream(a.getStream()));
		is.mark(READ_LIMIT);
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
				// need to mark again to re-add read limit
				is.mark(READ_LIMIT);
				size = getSizeFromBitmap(is);
			}
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		} finally {
			tryToClose(is, LOG, WARNING);
		}

		// calculate thumbnail size
		Size thumbnailSize = new Size(defaultSize, defaultSize, size.mimeType);
		if (!size.error) {
			thumbnailSize =
					getThumbnailSize(size.width, size.height, size.mimeType);
		}
		// get file extension
		String extension = imageHelper.getExtensionFromMimeType(size.mimeType);
		boolean hasError = extension == null || size.error;
		if (!h.getContentType().equals(size.mimeType)) {
			if (LOG.isLoggable(WARNING)) {
				LOG.warning("Header has different mime type (" +
						h.getContentType() + ") than image (" + size.mimeType +
						").");
			}
			hasError = true;
		}
		if (extension == null) extension = "";
		return new AttachmentItem(h, size.width, size.height, extension,
				thumbnailSize.width, thumbnailSize.height, hasError);
	}

	/**
	 * Gets the size of a JPEG {@link InputStream} if EXIF info is available.
	 */
	private Size getSizeFromExif(InputStream is) throws IOException {
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
			return new Size(height, width, "image/jpeg");
		}
		return new Size(width, height, "image/jpeg");
	}

	/**
	 * Gets the size of any image {@link InputStream}.
	 */
	private Size getSizeFromBitmap(InputStream is) {
		DecodeResult result = imageHelper.decodeStream(is);
		if (result.width < 1 || result.height < 1) return new Size();
		return new Size(result.width, result.height, result.mimeType);
	}

	private Size getThumbnailSize(int width, int height, String mimeType) {
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
		return new Size(thumbnailWidth, thumbnailHeight, mimeType);
	}

	private static class Size {

		private final int width;
		private final int height;
		private final String mimeType;
		private final boolean error;

		private Size(int width, int height, String mimeType) {
			this.width = width;
			this.height = height;
			this.mimeType = mimeType;
			this.error = false;
		}

		private Size() {
			this.width = 0;
			this.height = 0;
			this.mimeType = "";
			this.error = true;
		}
	}

}
