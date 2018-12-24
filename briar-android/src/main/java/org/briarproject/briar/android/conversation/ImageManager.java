package org.briarproject.briar.android.conversation;

import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;

@NotNullByDefault
interface ImageManager {

	void decodeStream(InputStream is, BitmapFactory.Options options);

	@Nullable
	String getExtensionFromMimeType(String mimeType);

}
