package org.briarproject.briar.android.attachment;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.AttachmentHeader;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.concurrent.Immutable;

import static java.util.Objects.requireNonNull;

@Immutable
@NotNullByDefault
public class AttachmentItem implements Parcelable {

	private final AttachmentHeader header;
	private final int width, height;
	private final String extension;
	private final int thumbnailWidth, thumbnailHeight;
	private final boolean hasError;
	private final long instanceId;

	public static final Creator<AttachmentItem> CREATOR =
			new Creator<AttachmentItem>() {
				@Override
				public AttachmentItem createFromParcel(Parcel in) {
					return new AttachmentItem(in);
				}

				@Override
				public AttachmentItem[] newArray(int size) {
					return new AttachmentItem[size];
				}
			};

	private static final AtomicLong NEXT_INSTANCE_ID = new AtomicLong(0);

	AttachmentItem(AttachmentHeader header, int width, int height,
			String extension, int thumbnailWidth, int thumbnailHeight,
			boolean hasError) {
		this.header = header;
		this.width = width;
		this.height = height;
		this.extension = extension;
		this.thumbnailWidth = thumbnailWidth;
		this.thumbnailHeight = thumbnailHeight;
		this.hasError = hasError;
		instanceId = NEXT_INSTANCE_ID.getAndIncrement();
	}

	protected AttachmentItem(Parcel in) {
		byte[] messageIdByte = new byte[MessageId.LENGTH];
		in.readByteArray(messageIdByte);
		MessageId messageId = new MessageId(messageIdByte);
		width = in.readInt();
		height = in.readInt();
		String mimeType = requireNonNull(in.readString());
		extension = requireNonNull(in.readString());
		thumbnailWidth = in.readInt();
		thumbnailHeight = in.readInt();
		hasError = in.readByte() != 0;
		instanceId = in.readLong();
		header = new AttachmentHeader(messageId, mimeType);
	}

	AttachmentHeader getHeader() {
		return header;
	}

	public MessageId getMessageId() {
		return header.getMessageId();
	}

	int getWidth() {
		return width;
	}

	int getHeight() {
		return height;
	}

	public String getMimeType() {
		return header.getContentType();
	}

	public String getExtension() {
		return extension;
	}

	public int getThumbnailWidth() {
		return thumbnailWidth;
	}

	public int getThumbnailHeight() {
		return thumbnailHeight;
	}

	public boolean hasError() {
		return hasError;
	}

	public String getTransitionName() {
		return String.valueOf(instanceId);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeByteArray(header.getMessageId().getBytes());
		dest.writeInt(width);
		dest.writeInt(height);
		dest.writeString(header.getContentType());
		dest.writeString(extension);
		dest.writeInt(thumbnailWidth);
		dest.writeInt(thumbnailHeight);
		dest.writeByte((byte) (hasError ? 1 : 0));
		dest.writeLong(instanceId);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		return o instanceof AttachmentItem &&
				instanceId == ((AttachmentItem) o).instanceId;
	}

}
