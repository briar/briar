package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.annotation.DrawableRes;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarApplication;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RecentEmojiPageModel implements EmojiPageModel {

	private static final Logger LOG =
			Logger.getLogger(RecentEmojiPageModel.class.getName());

	private static final String EMOJI_LRU_PREFERENCE = "pref_emoji_recent";
	private static final int EMOJI_LRU_SIZE = 50;

	private final LinkedHashSet<String> recentlyUsed; // UI thread

	@Inject
	SettingsManager settingsManager;

	@Inject
	@DatabaseExecutor
	Executor dbExecutor;

	RecentEmojiPageModel(Context context) {
		BriarApplication app =
				(BriarApplication) context.getApplicationContext();
		app.getApplicationComponent().inject(this);
		recentlyUsed = getPersistedCache();
	}

	private LinkedHashSet<String> getPersistedCache() {
		String serialized;
		try {
			// FIXME: Don't make DB calls on the UI thread
			Settings settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
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
		save(serialize(recentlyUsed));
	}

	private String serialize(LinkedHashSet<String> emojis) {
		return StringUtils.join(emojis, ";");
	}

	private LinkedHashSet<String> deserialize(@Nullable String serialized) {
		if (serialized == null) return new LinkedHashSet<>();
		String[] list = serialized.split(";");
		LinkedHashSet<String> result = new LinkedHashSet<>(list.length);
		Collections.addAll(result, list);
		return result;
	}

	private void save(final String serialized) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Settings settings = new Settings();
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

	private String[] toReversePrimitiveArray(LinkedHashSet<String> emojiSet) {
		String[] emojis = new String[emojiSet.size()];
		int i = emojiSet.size() - 1;
		for (String emoji : emojiSet) {
			emojis[i--] = emoji;
		}
		return emojis;
	}
}
