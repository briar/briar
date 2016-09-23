package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.R;
import org.briarproject.android.BaseActivity;
import org.briarproject.android.controller.DbController;
import org.briarproject.api.db.DbException;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.android.fragment.SettingsFragment.SETTINGS_NAMESPACE;

@UiThread
public class RecentEmojiPageModel implements EmojiPageModel {

	private static final Logger LOG =
			Logger.getLogger(RecentEmojiPageModel.class.getName());

	private static final String EMOJI_LRU_PREFERENCE = "pref_emoji_recent";
	private static final int EMOJI_LRU_SIZE = 50;

	private final LinkedHashSet<String> recentlyUsed;
	private Settings settings;

	@Inject
	SettingsManager settingsManager;
	@Inject
	DbController dbController;

	RecentEmojiPageModel(Context context) {
		if (!(context instanceof BaseActivity)) {
			throw new IllegalArgumentException(
					"Needs to be created from BaseActivity");
		}
		((BaseActivity) context).getActivityComponent().inject(this);
		recentlyUsed = getPersistedCache();
	}

	private LinkedHashSet<String> getPersistedCache() {
		String serialized;
		try {
			settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
			serialized = settings.get(EMOJI_LRU_PREFERENCE);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			serialized = null;
		}
		return deserialize(serialized);
	}

	@DrawableRes
	@Override
	public int getIcon() {
		return R.drawable.ic_emoji_recent;
	}

	@NonNull
	@Override
	public String[] getEmoji() {
		return toReversePrimitiveArray(recentlyUsed);
	}

	@Override
	public boolean hasSpriteMap() {
		return false;
	}

	@Override
	public String getSprite() {
		return null;
	}

	void onCodePointSelected(String emoji) {
		recentlyUsed.remove(emoji);
		recentlyUsed.add(emoji);

		if (recentlyUsed.size() > EMOJI_LRU_SIZE) {
			Iterator<String> iterator = recentlyUsed.iterator();
			iterator.next();
			iterator.remove();
		}
		save(recentlyUsed);
	}

	private String serialize(LinkedHashSet<String> emojis) {
		String result = "";
		for (String emoji : emojis) {
			result += emoji + ";";
		}
		if (!emojis.isEmpty())
			result = result.substring(0, result.length() - 1);
		return result;
	}

	private LinkedHashSet<String> deserialize(@Nullable String str) {
		String[] list = str == null ? new String[] {} : str.split(";");
		LinkedHashSet<String> result = new LinkedHashSet<>(list.length);
		Collections.addAll(result, list);
		return result;
	}

	private void save(final LinkedHashSet<String> recentlyUsed) {
		dbController.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				String serialized = serialize(recentlyUsed);
				settings.put(EMOJI_LRU_PREFERENCE, serialized);
				try {
					settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private String[] toReversePrimitiveArray(
			@NonNull LinkedHashSet<String> emojiSet) {
		String[] emojis = new String[emojiSet.size()];
		int i = emojiSet.size() - 1;
		for (String emoji : emojiSet) {
			emojis[i--] = emoji;
		}
		return emojis;
	}
}
