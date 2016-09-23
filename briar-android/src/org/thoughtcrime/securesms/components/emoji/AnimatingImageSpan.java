package org.thoughtcrime.securesms.components.emoji;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.support.annotation.UiThread;
import android.text.style.ImageSpan;

@UiThread
class AnimatingImageSpan extends ImageSpan {

	AnimatingImageSpan(Drawable drawable, Callback callback) {
		super(drawable, ALIGN_BOTTOM);
		drawable.setCallback(callback);
	}
}
