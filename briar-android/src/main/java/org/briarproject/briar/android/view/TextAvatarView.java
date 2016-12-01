package org.briarproject.briar.android.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.R;

import javax.annotation.Nullable;

import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

@UiThread
public class TextAvatarView extends FrameLayout {

	private final AppCompatTextView character;
	private final CircleImageView background;
	private final TextView badge;
	private int unreadCount;

	public TextAvatarView(Context context, @Nullable AttributeSet attrs) {
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
		character.setText(text.toUpperCase());
	}

	public void setUnreadCount(int count) {
		unreadCount = count;
		if (count > 0) {
			badge.setBackgroundResource(R.drawable.bubble);
			badge.setText(String.valueOf(count));
			badge.setTextColor(ContextCompat.getColor(getContext(),
					R.color.briar_text_primary_inverse));
			badge.setVisibility(VISIBLE);
		} else {
			badge.setVisibility(INVISIBLE);
		}
	}

	public void setProblem(boolean problem) {
		if (problem) {
			badge.setBackgroundResource(R.drawable.bubble_problem);
			badge.setText("!");
			badge.setTextColor(ContextCompat
					.getColor(getContext(), R.color.briar_primary));
			badge.setVisibility(VISIBLE);
		} else {
			setUnreadCount(unreadCount);
		}
	}

	public void setBackgroundBytes(byte[] bytes) {
		int r = getByte(bytes, 0) * 3 / 4 + 96;
		int g = getByte(bytes, 1) * 3 / 4 + 96;
		int b = getByte(bytes, 2) * 3 / 4 + 96;
		int color = Color.rgb(r, g, b);

		background.setImageDrawable(new ColorDrawable(color));
	}

	private byte getByte(byte[] bytes, int index) {
		if (bytes == null) {
			return -128;
		} else {
			return bytes[index % bytes.length];
		}
	}

	public void setAuthorAvatar(Author author) {
		Drawable drawable = new IdenticonDrawable(author.getId().getBytes());
		background.setImageDrawable(drawable);
		character.setVisibility(GONE);
	}

}
