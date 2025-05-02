package org.briarproject.briar.android.threaded;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.threaded.ThreadItemAdapter.ThreadItemListener;
import org.briarproject.briar.android.view.AuthorView;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

import androidx.annotation.CallSuper;
import androidx.annotation.UiThread;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;

import static android.text.util.Linkify.WEB_URLS;
import static android.text.util.Linkify.addLinks;
import static androidx.core.content.ContextCompat.getColor;
import static org.briarproject.bramble.util.StringUtils.trim;
import static org.briarproject.briar.android.util.UiUtils.makeLinksClickable;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@UiThread
@NotNullByDefault
public abstract class BaseThreadItemViewHolder<I extends ThreadItem>
		extends RecyclerView.ViewHolder implements Observer<String> {

	private final static int ANIMATION_DURATION = 5000;

	protected final TextView textView;
	private final ViewGroup layout;
	private final AuthorView author;
	@Nullable
	private ThreadItemListener<I> listener = null;
	@Nullable
	private LiveData<String> textLiveData = null;

	public BaseThreadItemViewHolder(View v) {
		super(v);

		layout = v.findViewById(R.id.layout);
		textView = v.findViewById(R.id.text);
		author = v.findViewById(R.id.author);
	}

	@CallSuper
	public void bind(I item, LifecycleOwner lifecycleOwner,
			ThreadItemListener<I> listener) {
		setText(item, lifecycleOwner, listener);

		author.setAuthor(item.getAuthor(), item.getAuthorInfo());
		author.setDate(item.getTimestamp());

		if (item.isHighlighted()) {
			layout.setActivated(true);
		} else if (!item.isRead()) {
			layout.setActivated(true);
			animateFadeOut();
		} else {
			layout.setActivated(false);
		}
	}

	protected void setText(I item, LifecycleOwner lifecycleOwner,
			ThreadItemListener<I> listener) {
		// Clear any existing text while we asynchronously load the new text
		textView.setText(null);
		// Remember the listener so we can use it to create links later
		this.listener = listener;
		// If the view has been re-bound and we're already asynchronously
		// loading text for another item, stop observing it
		if (textLiveData != null) textLiveData.removeObserver(this);
		// Asynchronously load the text for this item and observe the result
		textLiveData = listener.loadItemText(item.getId());
		textLiveData.observe(lifecycleOwner, this);
	}

	private void animateFadeOut() {
		setIsRecyclable(false);
		ValueAnimator anim = new ValueAnimator();
		int viewColor = getColor(getContext(), R.color.thread_item_highlight);
		anim.setIntValues(viewColor,
				getColor(getContext(), R.color.thread_item_background));
		anim.setEvaluator(new ArgbEvaluator());
		anim.setInterpolator(new AccelerateInterpolator());
		anim.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				layout.setBackgroundResource(
						R.drawable.list_item_thread_background);
				layout.setActivated(false);
				setIsRecyclable(true);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
			}

			@Override
			public void onAnimationRepeat(Animator animation) {
			}
		});
		anim.addUpdateListener(valueAnimator -> layout.setBackgroundColor(
				(Integer) valueAnimator.getAnimatedValue()));
		anim.setDuration(ANIMATION_DURATION);
		anim.start();
	}

	protected Context getContext() {
		return textView.getContext();
	}

	void onViewRecycled() {
		textView.setText(null);
		if (textLiveData != null) {
			textLiveData.removeObserver(this);
			textLiveData = null;
			listener = null;
		}
	}

	@Override
	public void onChanged(String s) {
		if (textLiveData != null) {
			textLiveData.removeObserver(this);
			textLiveData = null;
			textView.setText(trim(s));
			addLinks(textView, WEB_URLS);
			makeLinksClickable(textView, requireNonNull(listener)::onLinkClick);
			listener = null;
		}
	}
}
