package org.briarproject.android.threaded;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.android.view.AuthorView;
import org.briarproject.util.StringUtils;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

@UiThread
public abstract class ThreadItemViewHolder<I extends ThreadItem>
		extends RecyclerView.ViewHolder {

	private final static int ANIMATION_DURATION = 5000;

	private final TextView textView, lvlText, repliesText;
	private final AuthorView author;
	private final View[] lvls;
	private final View chevron, replyButton;
	private final ViewGroup cell;
	private final View topDivider;

	public ThreadItemViewHolder(View v) {
		super(v);

		textView = (TextView) v.findViewById(R.id.text);
		lvlText = (TextView) v.findViewById(R.id.nested_line_text);
		author = (AuthorView) v.findViewById(R.id.author);
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
		cell = (ViewGroup) v.findViewById(R.id.forum_cell);
		topDivider = v.findViewById(R.id.top_divider);
	}

	// TODO improve encapsulation, so we don't need to pass the adapter here
	public void bind(final ThreadItemAdapter<I> adapter,
			final ThreadItemListener<I> listener, final I item, int pos) {

		textView.setText(StringUtils.trim(item.getText()));

		if (pos == 0) {
			topDivider.setVisibility(View.INVISIBLE);
		} else {
			topDivider.setVisibility(View.VISIBLE);
		}

		for (int i = 0; i < lvls.length; i++) {
			lvls[i].setVisibility(i < item.getLevel() ? VISIBLE : GONE);
		}
		if (item.getLevel() > 5) {
			lvlText.setVisibility(VISIBLE);
			lvlText.setText("" + item.getLevel());
		} else {
			lvlText.setVisibility(GONE);
		}
		author.setAuthor(item.getAuthor());
		author.setDate(item.getTimestamp());
		author.setAuthorStatus(item.getStatus());

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
		if (item.equals(adapter.getReplyItem())) {
			cell.setBackgroundColor(ContextCompat
					.getColor(getContext(), R.color.forum_cell_highlight));
		} else if (item.equals(adapter.getAddedItem())) {
			cell.setBackgroundColor(ContextCompat
					.getColor(getContext(), R.color.forum_cell_highlight));
			animateFadeOut(adapter, adapter.getAddedItem());
			adapter.clearAddedItem();
		} else {
			cell.setBackgroundColor(ContextCompat
					.getColor(getContext(), R.color.window_background));
		}
		replyButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onReplyClick(item);
				adapter.scrollTo(item);
			}
		});
	}

	private void animateFadeOut(final ThreadItemAdapter<I> adapter,
			final I addedItem) {

		setIsRecyclable(false);
		ValueAnimator anim = new ValueAnimator();
		adapter.addAnimatingItem(addedItem, anim);
		ColorDrawable viewColor = (ColorDrawable) cell.getBackground();
		anim.setIntValues(viewColor.getColor(), ContextCompat
				.getColor(getContext(), R.color.window_background));
		anim.setEvaluator(new ArgbEvaluator());
		anim.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {

			}

			@Override
			public void onAnimationEnd(Animator animation) {
				setIsRecyclable(true);
				adapter.removeAnimatingItem(addedItem);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				setIsRecyclable(true);
				adapter.removeAnimatingItem(addedItem);
			}

			@Override
			public void onAnimationRepeat(Animator animation) {

			}
		});
		anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator valueAnimator) {
				cell.setBackgroundColor(
						(Integer) valueAnimator.getAnimatedValue());
			}
		});
		anim.setDuration(ANIMATION_DURATION);
		anim.start();
	}

	private Context getContext() {
		return textView.getContext();
	}

}
