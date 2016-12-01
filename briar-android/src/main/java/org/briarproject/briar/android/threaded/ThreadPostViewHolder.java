package org.briarproject.briar.android.threaded;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.threaded.ThreadItemAdapter.ThreadItemListener;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
public class ThreadPostViewHolder<I extends ThreadItem>
		extends BaseThreadItemViewHolder<I> {

	private final TextView  lvlText, repliesText;
	private final View[] lvls;
	private final View chevron, replyButton;

	public ThreadPostViewHolder(View v) {
		super(v);

		lvlText = (TextView) v.findViewById(R.id.nested_line_text);
		repliesText = (TextView) v.findViewById(R.id.replies);
		int[] nestedLineIds = {
				R.id.nested_line_1, R.id.nested_line_2, R.id.nested_line_3,
				R.id.nested_line_4, R.id.nested_line_5
		};
		lvls = new View[nestedLineIds.length];
		for (int i = 0; i < lvls.length; i++) {
			lvls[i] = v.findViewById(nestedLineIds[i]);
		}
		chevron = v.findViewById(R.id.chevron);
		replyButton = v.findViewById(R.id.btn_reply);
	}

	// TODO improve encapsulation, so we don't need to pass the adapter here
	@Override
	public void bind(final ThreadItemAdapter<I> adapter,
			final ThreadItemListener<I> listener, final I item, int pos) {
		super.bind(adapter, listener, item, pos);

		for (int i = 0; i < lvls.length; i++) {
			lvls[i].setVisibility(i < item.getLevel() ? VISIBLE : GONE);
		}
		if (item.getLevel() > 5) {
			lvlText.setVisibility(VISIBLE);
			lvlText.setText(String.valueOf(item.getLevel()));
		} else {
			lvlText.setVisibility(GONE);
		}

		int replies = adapter.getReplyCount(item);
		if (replies == 0) {
			repliesText.setText("");
		} else {
			repliesText.setText(getContext().getResources()
					.getQuantityString(R.plurals.message_replies, replies,
							replies));
		}

		if (item.hasDescendants()) {
			chevron.setVisibility(VISIBLE);
			if (item.isShowingDescendants()) {
				chevron.setSelected(false);
			} else {
				chevron.setSelected(true);
			}
			chevron.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					chevron.setSelected(!chevron.isSelected());
					if (chevron.isSelected()) {
						adapter.hideDescendants(item);
					} else {
						adapter.showDescendants(item);
					}
				}
			});
		} else {
			chevron.setVisibility(INVISIBLE);
		}
		replyButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onReplyClick(item);
				adapter.scrollTo(item);
			}
		});
	}

}
