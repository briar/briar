package org.briarproject.android.forum;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.NestedTreeList;
import org.briarproject.android.view.AuthorView;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.support.v7.widget.RecyclerView.NO_POSITION;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class NestedForumAdapter
		extends RecyclerView.Adapter<NestedForumAdapter.NestedForumHolder> {

	private static final int UNDEFINED = -1;

	private final NestedTreeList<ForumEntry> forumEntries =
			new NestedTreeList<>();
	private final Map<ForumEntry, ValueAnimator> animatingEntries =
			new HashMap<>();
	// highlight not dependant on time
	private ForumEntry replyEntry;
	// temporary highlight
	private ForumEntry addedEntry;
	private final Context ctx;
	private final OnNestedForumListener listener;
	private final LinearLayoutManager layoutManager;

	public NestedForumAdapter(Context ctx, OnNestedForumListener listener,
			LinearLayoutManager layoutManager) {
		this.ctx = ctx;
		this.listener = listener;
		this.layoutManager = layoutManager;
	}

	ForumEntry getReplyEntry() {
		return replyEntry;
	}

	void setEntries(List<ForumEntry> entries) {
		forumEntries.clear();
		forumEntries.addAll(entries);
		notifyItemRangeInserted(0, entries.size());
	}

	void addEntry(ForumEntry entry) {
		forumEntries.add(entry);
		addedEntry = entry;
		if (entry.getParentId() == null) {
			notifyItemInserted(getVisiblePos(entry));
		} else {
			// Try to find the entry's parent and perform the proper ui update if
			// it's present and visible.
			for (int i = forumEntries.indexOf(entry) - 1; i >= 0; i--) {
				ForumEntry higherEntry = forumEntries.get(i);
				if (higherEntry.getLevel() < entry.getLevel()) {
					// parent found
					if (higherEntry.isShowingDescendants()) {
						int parentVisiblePos = getVisiblePos(higherEntry);
						if (parentVisiblePos != NO_POSITION) {
							// parent is visible, we need to update its ui
							notifyItemChanged(parentVisiblePos);
							// new entry insert ui
							int visiblePos = getVisiblePos(entry);
							notifyItemInserted(visiblePos);
							break;
						}
					} else {
						// do not show the new entry if its parent is not showing
						// descendants (this can be overridden by the user by
						// pressing the snack bar)
						break;
					}
				}
			}
		}
	}

	void scrollToEntry(ForumEntry entry) {
		int visiblePos = getVisiblePos(entry);
		if (visiblePos == NO_POSITION && entry.getParentId() != null) {
			// The entry is not visible due to being hidden by its parent entry.
			// Find the parent and make it visible and traverse up the parent
			// chain if necessary to make the entry visible
			MessageId parentId = entry.getParentId();
			for (int i = forumEntries.indexOf(entry) - 1; i >= 0; i--) {
				ForumEntry higherEntry = forumEntries.get(i);
				if (higherEntry.getId().equals(parentId)) {
					// parent found
					showDescendants(higherEntry);
					int parentPos = getVisiblePos(higherEntry);
					if (parentPos != NO_POSITION) {
						// parent or ancestor is visible, entry's visibility
						// is ensured
						notifyItemChanged(parentPos);
						visiblePos = parentPos;
						break;
					}
					// parent or ancestor is hidden, we need to continue up the
					// dependency chain
					parentId = higherEntry.getParentId();
				}
			}
		}
		if (visiblePos != NO_POSITION)
			layoutManager.scrollToPositionWithOffset(visiblePos, 0);
	}

	private int getReplyCount(ForumEntry entry) {
		int counter = 0;
		int pos = forumEntries.indexOf(entry);
		if (pos >= 0) {
			int ancestorLvl = entry.getLevel();
			for (int i = pos + 1; i < forumEntries.size(); i++) {
				int descendantLvl = forumEntries.get(i).getLevel();
				if (descendantLvl <= ancestorLvl)
					break;
				if (descendantLvl == ancestorLvl + 1)
					counter++;
			}
		}
		return counter;
	}

	void setReplyEntryById(byte[] id) {
		MessageId messageId = new MessageId(id);
		for (ForumEntry entry : forumEntries) {
			if (entry.getId().equals(messageId)) {
				setReplyEntry(entry);
				break;
			}
		}
	}

	void setReplyEntry(ForumEntry entry) {
		if (replyEntry != null) {
			notifyItemChanged(getVisiblePos(replyEntry));
		}
		replyEntry = entry;
		if (replyEntry != null) {
			notifyItemChanged(getVisiblePos(replyEntry));
		}
	}

	private List<Integer> getSubTreeIndexes(int pos, int levelLimit) {
		List<Integer> indexList = new ArrayList<>();

		for (int i = pos + 1; i < getItemCount(); i++) {
			ForumEntry entry = getVisibleEntry(i);
			if (entry != null && entry.getLevel() > levelLimit) {
				indexList.add(i);
			} else {
				break;
			}
		}
		return indexList;
	}

	void showDescendants(ForumEntry forumEntry) {
		forumEntry.setShowingDescendants(true);
		int visiblePos = getVisiblePos(forumEntry);
		List<Integer> indexList =
				getSubTreeIndexes(visiblePos, forumEntry.getLevel());
		if (!indexList.isEmpty()) {
			if (indexList.size() == 1) {
				notifyItemInserted(indexList.get(0));
			} else {
				notifyItemRangeInserted(indexList.get(0),
						indexList.size());
			}
		}
	}

	void hideDescendants(ForumEntry forumEntry) {
		int visiblePos = getVisiblePos(forumEntry);
		List<Integer> indexList =
				getSubTreeIndexes(visiblePos, forumEntry.getLevel());
		if (!indexList.isEmpty()) {
			// stop animating children
			for (int index : indexList) {
				ValueAnimator anim =
						animatingEntries.get(forumEntries.get(index));
				if (anim != null && anim.isRunning()) {
					anim.cancel();
				}
			}
			if (indexList.size() == 1) {
				notifyItemRemoved(indexList.get(0));
			} else {
				notifyItemRangeRemoved(indexList.get(0),
						indexList.size());
			}
		}
		forumEntry.setShowingDescendants(false);
	}


	/**
	 *
	 * @param position is visible entry index
	 * @return the visible entry at index position from an ordered list of visible
	 * entries, or null if position is larger then the number of visible entries.
	 */
	@Nullable
	ForumEntry getVisibleEntry(int position) {
		int levelLimit = UNDEFINED;
		for (ForumEntry forumEntry : forumEntries) {
			if (levelLimit >= 0) {
				// skip hidden entries that their parent is hiding
				if (forumEntry.getLevel() > levelLimit) {
					continue;
				}
				levelLimit = UNDEFINED;
			}
			if (!forumEntry.isShowingDescendants()) {
				levelLimit = forumEntry.getLevel();
			}
			if (position-- == 0) {
				return forumEntry;
			}
		}
		return null;
	}

	private void animateFadeOut(final NestedForumHolder ui,
			final ForumEntry addedEntry) {
		ui.setIsRecyclable(false);
		ValueAnimator anim = new ValueAnimator();
		animatingEntries.put(addedEntry, anim);
		ColorDrawable viewColor = (ColorDrawable) ui.cell.getBackground();
		anim.setIntValues(viewColor.getColor(), ContextCompat
				.getColor(ctx, R.color.window_background));
		anim.setEvaluator(new ArgbEvaluator());
		anim.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {

			}

			@Override
			public void onAnimationEnd(Animator animation) {
				ui.setIsRecyclable(true);
				animatingEntries.remove(addedEntry);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				ui.setIsRecyclable(true);
				animatingEntries.remove(addedEntry);
			}

			@Override
			public void onAnimationRepeat(Animator animation) {

			}
		});
		anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator valueAnimator) {
				ui.cell.setBackgroundColor(
						(Integer) valueAnimator.getAnimatedValue());
			}
		});
		anim.setDuration(5000);
		anim.start();
	}

	@Override
	public NestedForumHolder onCreateViewHolder(ViewGroup parent,
			int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.list_item_forum_post, parent, false);
		return new NestedForumHolder(v);
	}

	@Override
	public void onBindViewHolder(
			final NestedForumHolder ui, final int position) {
		final ForumEntry entry = getVisibleEntry(position);
		if (entry == null) return;
		listener.onEntryVisible(entry);

		ui.textView.setText(StringUtils.trim(entry.getText()));

		if (position == 0) {
			ui.topDivider.setVisibility(View.INVISIBLE);
		} else {
			ui.topDivider.setVisibility(View.VISIBLE);
		}

		for (int i = 0; i < ui.lvls.length; i++) {
			ui.lvls[i].setVisibility(i < entry.getLevel() ? VISIBLE : GONE);
		}
		if (entry.getLevel() > 5) {
			ui.lvlText.setVisibility(VISIBLE);
			ui.lvlText.setText("" + entry.getLevel());
		} else {
			ui.lvlText.setVisibility(GONE);
		}
		ui.author.setAuthor(entry.getAuthor());
		ui.author.setDate(entry.getTimestamp());
		ui.author.setAuthorStatus(entry.getStatus());

		int replies = getReplyCount(entry);
		if (replies == 0) {
			ui.repliesText.setText("");
		} else {
			ui.repliesText.setText(
					ctx.getResources()
							.getQuantityString(R.plurals.message_replies,
									replies, replies));
		}

		if (entry.hasDescendants()) {
			ui.chevron.setVisibility(VISIBLE);
			if (entry.isShowingDescendants()) {
				ui.chevron.setSelected(false);
			} else {
				ui.chevron.setSelected(true);
			}
			ui.chevron.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ui.chevron.setSelected(!ui.chevron.isSelected());
					if (ui.chevron.isSelected()) {
						hideDescendants(entry);
					} else {
						showDescendants(entry);
					}
				}
			});
		} else {
			ui.chevron.setVisibility(INVISIBLE);
		}
		if (entry.equals(replyEntry)) {
			ui.cell.setBackgroundColor(ContextCompat
					.getColor(ctx, R.color.forum_cell_highlight));
		} else if (entry.equals(addedEntry)) {

			ui.cell.setBackgroundColor(ContextCompat
					.getColor(ctx, R.color.forum_cell_highlight));
			animateFadeOut(ui, addedEntry);
			addedEntry = null;
		} else {
			ui.cell.setBackgroundColor(ContextCompat
					.getColor(ctx, R.color.window_background));
		}
		ui.replyButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onReplyClick(entry);
				scrollToEntry(entry);
			}
		});
	}

	public boolean isVisible(ForumEntry entry) {
		return getVisiblePos(entry) != NO_POSITION;
	}

	/**
	 * @param sEntry the ForumEntry to find the visible positoin of, or null to
	 *               return the total cound of visible elements
	 * @return the visible position of sEntry, or the total number of visible
	 * elements if sEntry is null. If sEntry is not visible a NO_POSITION is
	 * returned.
	 */
	private int getVisiblePos(ForumEntry sEntry) {
		int visibleCounter = 0;
		int levelLimit = UNDEFINED;
		for (ForumEntry fEntry : forumEntries) {
			if (levelLimit >= 0) {
				if (fEntry.getLevel() > levelLimit) {
					// skip all the entries below a non visible branch
					continue;
				}
				levelLimit = UNDEFINED;
			}
			if (sEntry != null && sEntry.equals(fEntry)) {
				return visibleCounter;
			} else if (!fEntry.isShowingDescendants()) {
				levelLimit = fEntry.getLevel();
			}
			visibleCounter++;
		}
		return sEntry == null ? visibleCounter : NO_POSITION;
	}

	@Override
	public int getItemCount() {
		return getVisiblePos(null);
	}

	static class NestedForumHolder extends RecyclerView.ViewHolder {

		final TextView textView, lvlText, repliesText;
		final AuthorView author;
		final View[] lvls;
		final View chevron, replyButton;
		final ViewGroup cell;
		final View topDivider;

		NestedForumHolder(View v) {
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

	}

	interface OnNestedForumListener {
		void onEntryVisible(ForumEntry forumEntry);

		void onReplyClick(ForumEntry forumEntry);
	}
}
