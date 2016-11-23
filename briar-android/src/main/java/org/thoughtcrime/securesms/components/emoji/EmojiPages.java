package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;

import org.briarproject.briar.R;

import java.util.Arrays;
import java.util.List;

class EmojiPages {

	static List<EmojiPageModel> getPages(Context ctx) {
		return Arrays.<EmojiPageModel>asList(
				new StaticEmojiPageModel(ctx, R.drawable.ic_emoji_smiley_people,
						R.array.emoji_smiley_people,
						"emoji_smiley_people.png"),
				new StaticEmojiPageModel(ctx,
						R.drawable.ic_emoji_animals_nature,
						R.array.emoji_animals_nature,
						"emoji_animals_nature.png"),
				new StaticEmojiPageModel(ctx, R.drawable.ic_emoji_food_drink,
						R.array.emoji_food_drink,
						"emoji_food_drink.png"),
				new StaticEmojiPageModel(ctx, R.drawable.ic_emoji_travel_places,
						R.array.emoji_travel_places,
						"emoji_travel_places.png"),
				new StaticEmojiPageModel(ctx, R.drawable.ic_emoji_activity,
						R.array.emoji_activity,
						"emoji_activity.png"),
				new StaticEmojiPageModel(ctx, R.drawable.ic_emoji_objects,
						R.array.emoji_objects,
						"emoji_objects.png"),
				new StaticEmojiPageModel(ctx, R.drawable.ic_emoji_symbols,
						R.array.emoji_symbols,
						"emoji_symbols.png"),
				new StaticEmojiPageModel(ctx, R.drawable.ic_emoji_flags,
						R.array.emoji_flags,
						"emoji_flags.png"),

				new StaticEmojiPageModel(R.drawable.ic_emoji_emoticons,
						new String[] {
								":-)", ";-)", "(-:", ":->", ":-D", "\\o/",
								":-P", "B-)", ":-$", ":-*", "O:-)", "=-O",
								"O_O", "O_o", "o_O", ":O", ":-!", ":-x",
								":-|", ":-\\", ":-(", ":'(", ":-[", ">:-(",
								"^.^", "^_^", "\\(\u02c6\u02da\u02c6)/",
								"\u30fd(\u00b0\u25c7\u00b0 )\u30ce",
								"\u00af\\(\u00b0_o)/\u00af",
								"\u00af\\_(\u30c4)_/\u00af", "(\u00ac_\u00ac)",
								"(>_<)", "(\u2565\ufe4f\u2565)",
								"(\u261e\uff9f\u30ee\uff9f)\u261e",
								"\u261c(\uff9f\u30ee\uff9f\u261c)",
								"\u261c(\u2312\u25bd\u2312)\u261e",
								"(\u256f\u00b0\u25a1\u00b0)\u256f\ufe35",
								"\u253b\u2501\u253b",
								"\u252c\u2500\u252c",
								"\u30ce(\u00b0\u2013\u00b0\u30ce)",
								"(^._.^)\uff89",
								"\u0e05^\u2022\ufecc\u2022^\u0e05",
								"(\u2022_\u2022)",
								" \u25a0-\u25a0\u00ac <(\u2022_\u2022) ",
								"(\u25a0_\u25a0\u00ac)"
						}, null));
	}
}
