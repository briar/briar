package org.briarproject.briar.android.conversation;

import android.os.Parcel;
import android.os.Parcelable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentItem implements Parcelable {

	private final MessageId messageId;
	private final int width, height;
	private final int thumbnailWidth, thumbnailHeight;
	private final boolean hasError;

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

	AttachmentItem(MessageId messageId, int width, int height,
			int thumbnailWidth, int thumbnailHeight, boolean hasError) {
		this.messageId = messageId;
		this.width = width;
		this.height = height;
		this.thumbnailWidth = thumbnailWidth;
		this.thumbnailHeight = thumbnailHeight;
		this.hasError = hasError;
	}

	protected AttachmentItem(Parcel in) {
		byte[] messageIdByte = new byte[MessageId.LENGTH];
		in.readByteArray(messageIdByte);
		messageId = new MessageId(messageIdByte);
		width = in.readInt();
		height = in.readInt();
		thumbnailWidth = in.readInt();
		thumbnailHeight = in.readInt();
		hasError = in.readByte() != 0;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	int getWidth() {
		return width;
	}

	int getHeight() {
		return height;
	}

	int getThumbnailWidth() {
		return thumbnailWidth;
	}

	int getThumbnailHeight() {
		return thumbnailHeight;
	}

	boolean hasError() {
		return hasError;
	}

	// TODO use counter instead, because in theory one attachment can appear in more than one messages
	String getTransitionName() {
		return String.valueOf(messageId.hashCode());
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeByteArray(messageId.getBytes());
		dest.writeInt(width);
		dest.writeInt(height);
		dest.writeInt(thumbnailWidth);
		dest.writeInt(thumbnailHeight);
		dest.writeByte((byte) (hasError ? 1 : 0));
	}

}
