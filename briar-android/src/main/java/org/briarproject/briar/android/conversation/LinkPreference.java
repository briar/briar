package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import org.briarproject.briar.R;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

class LinkPreference extends Preference {

	public LinkPreference(Context context,
			AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	public LinkPreference(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	public LinkPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public LinkPreference(Context context) {
		super(context);
		init();
	}

	private void init() {
		setLayoutResource(
				R.layout.conversation_settings_disappearing_messages_learn_more);
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		TextView link = (TextView) holder.findViewById(R.id.link);

		SpannableStringBuilder ssb = new SpannableStringBuilder();
		ssb.append(link.getText());
		ssb.setSpan(new URLSpan("#"), 0, ssb.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		link.setText(ssb, TextView.BufferType.SPANNABLE);
	}

}
