package org.thoughtcrime.securesms.components.emoji;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

import javax.annotation.Nullable;

interface EmojiPageModel {

	@DrawableRes
	int getIcon();

	@NonNull
	String[] getEmoji();

	boolean hasSpriteMap();

	@Nullable
	String getSprite();
}
