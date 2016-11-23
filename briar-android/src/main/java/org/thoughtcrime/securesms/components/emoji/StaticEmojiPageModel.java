package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.annotation.ArrayRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;

import javax.annotation.Nullable;

@UiThread
class StaticEmojiPageModel implements EmojiPageModel {

	@DrawableRes
	private final int icon;
	@NonNull
	private final String[] emoji;
	@Nullable
	private final String sprite;

	StaticEmojiPageModel(@DrawableRes int icon, @NonNull String[] emoji,
			@Nullable String sprite) {
		this.icon = icon;
		this.emoji = emoji;
		this.sprite = sprite;
	}

	StaticEmojiPageModel(Context ctx, @DrawableRes int icon,
			@ArrayRes int res, @Nullable String sprite) {
		this(icon, getEmoji(ctx, res), sprite);
	}

	@DrawableRes
	@Override
	public int getIcon() {
		return icon;
	}

	@Override
	@NonNull
	public String[] getEmoji() {
		return emoji;
	}

	@Override
	public boolean hasSpriteMap() {
		return sprite != null;
	}

	@Nullable
	@Override
	public String getSprite() {
		return sprite;
	}

	@NonNull
	private static String[] getEmoji(Context ctx, @ArrayRes int res) {
		String[] rawStrings = ctx.getResources().getStringArray(res);
		String[] emoji = new String[rawStrings.length];
		int i = 0;
		for (String codePoint : rawStrings) {
			String[] bytes = codePoint.split(",");
			int[] codePoints = new int[bytes.length];
			int j = 0;
			for (String b : bytes) {
				codePoints[j] = Integer.valueOf(b, 16);
			}
			emoji[i] = new String(codePoints, 0, codePoints.length);
			i++;
		}
		return emoji;
	}

}
