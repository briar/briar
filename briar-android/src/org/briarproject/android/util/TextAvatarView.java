package org.briarproject.android.util;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.briarproject.R;

import de.hdodenhof.circleimageview.CircleImageView;

public class TextAvatarView extends FrameLayout {

	final private AppCompatTextView character;
	final private CircleImageView background;
	final private TextView badge;
	private int unreadCount;

	public TextAvatarView(Context context, AttributeSet attrs) {
		super(context, attrs);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater
				.inflate(R.layout.text_avatar_view, this, true);
		character = (AppCompatTextView) findViewById(R.id.textAvatarView);
		background = (CircleImageView) findViewById(R.id.avatarBackground);
		badge = (TextView) findViewById(R.id.unreadCountView);
		badge.setVisibility(INVISIBLE);
	}

	public TextAvatarView(Context context) {
		this(context, null);
	}

	public void setText(String text) {
		character.setText(text);
	}

	public void setUnreadCount(int count) {
		if (count > 0) {
			this.unreadCount = count;
			badge.setBackgroundResource(R.drawable.bubble);
			badge.setText(String.valueOf(count));
			badge.setTextColor(ContextCompat.getColor(getContext(), R.color.briar_text_primary_inverse));
			badge.setVisibility(VISIBLE);
		} else {
			badge.setVisibility(INVISIBLE);
		}
	}

	public void setProblem(boolean problem) {
		if (problem) {
			badge.setBackgroundResource(R.drawable.bubble_problem);
			badge.setText("!");
			badge.setTextColor(ContextCompat.getColor(getContext(), R.color.briar_primary));
			badge.setVisibility(VISIBLE);
		} else if (unreadCount > 0) {
			setUnreadCount(unreadCount);
		} else {
			badge.setVisibility(INVISIBLE);
		}
	}

	public void setBackgroundBytes(byte[] bytes) {
		int r = getByte(bytes, 0) * 3 / 4 + 96;
		int g = getByte(bytes, 1) * 3 / 4 + 96;
		int b = getByte(bytes, 2) * 3 / 4 + 96;
		int color = Color.rgb(r, g, b);

		background.setFillColor(color);
	}

	private byte getByte(byte[] bytes, int index) {
		if (bytes == null) {
			return -128;
		} else {
			return bytes[index % bytes.length];
		}
	}

}
