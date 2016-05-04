package org.briarproject.android.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import org.briarproject.R;

import de.hdodenhof.circleimageview.CircleImageView;

public class TextAvatarView extends FrameLayout {

	final private AppCompatTextView textView;
	final private CircleImageView backgroundView;

	public TextAvatarView(Context context, AttributeSet attrs) {
		super(context, attrs);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater
				.inflate(R.layout.text_avatar_view, this, true);
		textView = (AppCompatTextView) findViewById(R.id.textAvatarView);
		backgroundView = (CircleImageView) findViewById(R.id.avatarBackground);
	}

	public TextAvatarView(Context context) {
		this(context, null);
	}

	public void setText(String text) {
		textView.setText(text);
	}

	public void setBackgroundBytes(byte[] bytes) {
		int r = getByte(bytes, 0) * 3 / 4 + 96;
		int g = getByte(bytes, 1) * 3 / 4 + 96;
		int b = getByte(bytes, 2) * 3 / 4 + 96;
		int color = Color.rgb(r, g, b);

		backgroundView.setFillColor(color);
	}

	private byte getByte(byte[] bytes, int index) {
		if (bytes == null) {
			return -128;
		} else {
			return bytes[index % bytes.length];
		}
	}

}
