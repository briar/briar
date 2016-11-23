package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;

import org.briarproject.briar.R;

import javax.annotation.Nullable;

@UiThread
public class EmojiPageView extends FrameLayout {

	private final GridView grid;

	private EmojiSelectionListener listener;

	public EmojiPageView(Context context) {
		this(context, null);
	}

	public EmojiPageView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiPageView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		final View view = LayoutInflater.from(getContext())
				.inflate(R.layout.emoji_grid_layout, this, true);
		grid = (GridView) view.findViewById(R.id.emoji);
		grid.setColumnWidth(getResources()
				.getDimensionPixelSize(R.dimen.emoji_drawer_size) + 2 *
				getResources().getDimensionPixelSize(
						R.dimen.emoji_drawer_item_padding));
		grid.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (listener != null)
					listener.onEmojiSelected(((EmojiView) view).getEmoji());
			}
		});
	}

	public void setModel(EmojiPageModel model) {
		grid.setAdapter(new EmojiGridAdapter(getContext(), model));
	}

	public void setEmojiSelectedListener(EmojiSelectionListener listener) {
		this.listener = listener;
	}

	private static class EmojiGridAdapter extends BaseAdapter {

		private final Context context;
		private final EmojiPageModel model;
		private final int emojiSize;

		private EmojiGridAdapter(Context context, EmojiPageModel model) {
			this.context = context;
			this.model = model;
			emojiSize = (int) context.getResources()
					.getDimension(R.dimen.emoji_drawer_size);
		}

		@Override
		public int getCount() {
			return model.getEmoji().length;
		}

		@Nullable
		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			EmojiView view;
			int pad = context.getResources()
					.getDimensionPixelSize(R.dimen.emoji_drawer_item_padding);
			if (convertView != null && convertView instanceof EmojiView) {
				view = (EmojiView) convertView;
			} else {
				EmojiView emojiView = new EmojiView(context);
				emojiView.setPadding(pad, pad, pad, pad);
				emojiView.setLayoutParams(
						new AbsListView.LayoutParams(emojiSize + 2 * pad,
								emojiSize + 2 * pad));
				view = emojiView;
			}

			view.setEmoji(model.getEmoji()[position]);
			return view;
		}
	}

	interface EmojiSelectionListener {
		void onEmojiSelected(String emoji);
	}
}
