package org.thoughtcrime.securesms.components.emoji;

import android.support.annotation.UiThread;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.TextView;

import javax.annotation.Nullable;

@UiThread
class EmojiFilter implements InputFilter {

	private final TextView view;

	EmojiFilter(TextView view) {
		this.view = view;
	}

	@Nullable
	@Override
	public CharSequence filter(CharSequence source, int start, int end,
			Spanned dest, int dstart, int dend) {

		char[] v = new char[end - start];
		TextUtils.getChars(source, start, end, v, 0);
		Spannable emojified = EmojiProvider.getInstance(view.getContext())
				.emojify(new String(v), view);
		if (source instanceof Spanned && emojified != null) {
			TextUtils.copySpansFrom((Spanned) source, start, end, null,
					emojified, 0);
		}
		return emojified;
	}
}
