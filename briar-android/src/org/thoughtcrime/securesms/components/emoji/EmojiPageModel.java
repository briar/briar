package org.thoughtcrime.securesms.components.emoji;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface EmojiPageModel {
	@DrawableRes
	int getIcon();

	@NonNull
	String[] getEmoji();

	boolean hasSpriteMap();

	@Nullable
	String getSprite();
}
