package org.briarproject.android.forum;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.sharing.ShareForumActivity;
import org.briarproject.android.sharing.SharingStatusForumActivity;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.android.util.TrustIndicatorView;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import im.delight.android.identicons.IdenticonDrawable;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.support.v7.widget.RecyclerView.NO_POSITION;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;

public class ForumActivity extends BriarActivity implements
		ForumController.ForumPostListener {

	static final String FORUM_NAME = "briar.FORUM_NAME";

	private static final int REQUEST_FORUM_SHARED = 3;
	private static final int UNDEFINED = -1;
	private static final String KEY_INPUT_VISIBILITY = "inputVisibility";
	private static final String KEY_REPLY_ID = "replyId";

	@Inject
	AndroidNotificationManager notificationManager;

	// uncomment the next line for a test component with dummy data
//	@Named("ForumTestController")
	@Inject
	protected ForumController forumController;

	// Protected access for testing
	protected ForumAdapter forumAdapter;

	private BriarRecyclerView recyclerView;
	private EditText textInput;
	private ViewGroup inputContainer;
	private LinearLayoutManager linearLayoutManager;

	private volatile GroupId groupId = null;

	@Override
	public void onCreate(final Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_forum);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String forumName = i.getStringExtra(FORUM_NAME);
		if (forumName != null) setTitle(forumName);

		forumAdapter = new ForumAdapter();

		inputContainer = (ViewGroup) findViewById(R.id.text_input_container);
		inputContainer.setVisibility(GONE);
		textInput = (EditText) findViewById(R.id.input_text);
		recyclerView =
				(BriarRecyclerView) findViewById(R.id.forum_discussion_list);
		recyclerView.setAdapter(forumAdapter);
		linearLayoutManager = new LinearLayoutManager(this);
		recyclerView.setLayoutManager(linearLayoutManager);
		recyclerView.setEmptyText(R.string.no_forum_posts);

		forumController.loadForum(groupId, new UiResultHandler<Boolean>(this) {
			@Override
			public void onResultUi(Boolean result) {
				if (result) {
					Forum forum = forumController.getForum();
					if (forum != null) setTitle(forum.getName());
					List<ForumEntry> entries =
							forumController.getForumEntries();
					if (entries.isEmpty()) {
						recyclerView.showData();
					} else {
						forumAdapter.setEntries(entries);
						if (state != null) {
							byte[] replyId = state.getByteArray(KEY_REPLY_ID);
							if (replyId != null)
								forumAdapter.setReplyEntryById(replyId);
						}
					}
				} else {
					// TODO Maybe an error dialog ?
					finish();
				}
			}
		});
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		inputContainer.setVisibility(
				savedInstanceState.getBoolean(KEY_INPUT_VISIBILITY) ?
						VISIBLE : GONE);
	}


	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(KEY_INPUT_VISIBILITY,
				inputContainer.getVisibility() == VISIBLE);
		ForumEntry replyEntry = forumAdapter.getReplyEntry();
		if (replyEntry != null) {
			outState.putByteArray(KEY_REPLY_ID,
					replyEntry.getMessageId().getBytes());
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void displaySnackbarShort(int stringId) {
		Snackbar snackbar =
				Snackbar.make(recyclerView, stringId, Snackbar.LENGTH_SHORT);
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		snackbar.show();
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_FORUM_SHARED && result == RESULT_OK) {
			displaySnackbarShort(R.string.forum_shared_snackbar);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.forum_actions, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onBackPressed() {
		if (inputContainer.getVisibility() == VISIBLE) {
			inputContainer.setVisibility(GONE);
			forumAdapter.setReplyEntry(null);
		} else {
			super.onBackPressed();
		}
	}

	private void showTextInput(ForumEntry replyEntry) {
		// An animation here would be an overkill because of the keyboard
		// popping up.
		// only clear the text when the input container was not visible
		if (inputContainer.getVisibility() != VISIBLE) {
			inputContainer.setVisibility(VISIBLE);
			textInput.setText("");
		}
		textInput.requestFocus();
		textInput.setHint(replyEntry == null ? R.string.forum_new_message_hint :
				R.string.forum_message_reply_hint);
		showSoftKeyboardForced(textInput);
		forumAdapter.setReplyEntry(replyEntry);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		ActivityOptionsCompat options = ActivityOptionsCompat
				.makeCustomAnimation(this, android.R.anim.slide_in_left,
						android.R.anim.slide_out_right);
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_forum_compose_post:
				showTextInput(null);
				return true;
			case R.id.action_forum_share:
				Intent i2 = new Intent(this, ShareForumActivity.class);
				i2.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i2.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat
						.startActivityForResult(this, i2, REQUEST_FORUM_SHARED,
								options.toBundle());
				return true;
			case R.id.action_forum_sharing_status:
				Intent i3 = new Intent(this, SharingStatusForumActivity.class);
				i3.setFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
				i3.putExtra(GROUP_ID, groupId.getBytes());
				ActivityCompat.startActivity(this, i3, options.toBundle());
				return true;
			case R.id.action_forum_delete:
				showUnsubscribeDialog();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		notificationManager.blockNotification(groupId);
		notificationManager.clearForumPostNotification(groupId);
		recyclerView.startPeriodicUpdate();
	}

	@Override
	public void onPause() {
		super.onPause();
		notificationManager.unblockNotification(groupId);
		recyclerView.stopPeriodicUpdate();
	}

	public void sendMessage(View view) {
		String text = textInput.getText().toString();
		if (text.trim().length() == 0)
			return;
		if (forumController.getForum() == null) return;
		ForumEntry replyEntry = forumAdapter.getReplyEntry();
		if (replyEntry == null) {
			// root post
			forumController.createPost(StringUtils.toUtf8(text));
		} else {
			forumController.createPost(StringUtils.toUtf8(text),
					replyEntry.getMessageId());
		}
		hideSoftKeyboard(textInput);
		inputContainer.setVisibility(GONE);
		forumAdapter.setReplyEntry(null);
	}

	private void showUnsubscribeDialog() {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						forumController.unsubscribe(
								new UiResultHandler<Boolean>(
										ForumActivity.this) {
									@Override
									public void onResultUi(Boolean result) {
										if (result) {
											Toast.makeText(ForumActivity.this,
													R.string.forum_left_toast,
													LENGTH_SHORT)
													.show();
										}
									}
								});
					}
				};
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ForumActivity.this,
						R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_leave_forum));
		builder.setMessage(getString(R.string.dialog_message_leave_forum));
		builder.setPositiveButton(R.string.dialog_button_leave, okListener);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	@Override
	public void addLocalEntry(int index, ForumEntry entry) {
		forumAdapter.addEntry(index, entry, true);
		displaySnackbarShort(R.string.forum_new_entry_posted);
	}

	@Override
	public void addForeignEntry(final int index, final ForumEntry entry) {
		forumAdapter.addEntry(index, entry, false);
		Snackbar snackbar =
				Snackbar.make(recyclerView, R.string.forum_new_entry_received,
						Snackbar.LENGTH_LONG);
		snackbar.setActionTextColor(
				ContextCompat.getColor(this, R.color.briar_button_positive));
		snackbar.setAction(R.string.show, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				forumAdapter.scrollToEntry(entry);
			}
		});
		snackbar.getView().setBackgroundResource(R.color.briar_primary);
		snackbar.show();
	}

	static class ForumViewHolder extends RecyclerView.ViewHolder {

		final TextView textView, lvlText, authorText, dateText, repliesText;
		final View[] lvls;
		public final ImageView avatar;
		final TrustIndicatorView trust;
		final View chevron, replyButton;
		final ViewGroup cell;
		final View topDivider;

		ForumViewHolder(View v) {
			super(v);

			textView = (TextView) v.findViewById(R.id.text);
			lvlText = (TextView) v.findViewById(R.id.nested_line_text);
			authorText = (TextView) v.findViewById(R.id.author);
			dateText = (TextView) v.findViewById(R.id.date);
			repliesText = (TextView) v.findViewById(R.id.replies);
			int[] nestedLineIds = {
					R.id.nested_line_1, R.id.nested_line_2, R.id.nested_line_3,
					R.id.nested_line_4, R.id.nested_line_5
			};
			lvls = new View[nestedLineIds.length];
			for (int i = 0; i < lvls.length; i++) {
				lvls[i] = v.findViewById(nestedLineIds[i]);
			}
			avatar = (ImageView) v.findViewById(R.id.avatar);
			trust = (TrustIndicatorView) v.findViewById(R.id.trustIndicator);
			chevron = v.findViewById(R.id.chevron);
			replyButton = v.findViewById(R.id.btn_reply);
			cell = (ViewGroup) v.findViewById(R.id.forum_cell);
			topDivider = v.findViewById(R.id.top_divider);
		}
	}

	public class ForumAdapter extends RecyclerView.Adapter<ForumViewHolder> {

		private final List<ForumEntry> forumEntries = new ArrayList<>();
		private final Map<ForumEntry, ValueAnimator> animatingEntries =
				new HashMap<>();

		// highlight not dependant on time
		private ForumEntry replyEntry;
		// temporary highlight
		private ForumEntry addedEntry;

		private ForumEntry getReplyEntry() {
			return replyEntry;
		}

		void setEntries(List<ForumEntry> entries) {
			forumEntries.clear();
			forumEntries.addAll(entries);
			notifyItemRangeInserted(0, entries.size());
		}

		void addEntry(int index, ForumEntry entry, boolean isScrolling) {
			forumEntries.add(index, entry);
			boolean isShowingDescendants = false;
			if (entry.getLevel() > 0) {
				// update parent and make sure descendants are visible
				// Note that the parent's visibility is guaranteed (otherwise
				// the reply button would not be visible)
				for (int i = index - 1; i >= 0; i--) {
					ForumEntry higherEntry = forumEntries.get(i);
					if (higherEntry.getLevel() < entry.getLevel()) {
						// parent found
						if (!higherEntry.isShowingDescendants()) {
							isShowingDescendants = true;
							showDescendants(higherEntry);
						}
						notifyItemChanged(getVisiblePos(higherEntry));
						break;
					}
				}
			}
			if (!isShowingDescendants) {
				int visiblePos = getVisiblePos(entry);
				notifyItemInserted(visiblePos);
				if (isScrolling)
					linearLayoutManager
							.scrollToPositionWithOffset(visiblePos, 0);
			}
			addedEntry = entry;
		}

		void scrollToEntry(ForumEntry entry) {
			int visiblePos = getVisiblePos(entry);
			linearLayoutManager.scrollToPositionWithOffset(visiblePos, 0);
		}

		private boolean hasDescendants(ForumEntry forumEntry) {
			int i = forumEntries.indexOf(forumEntry);
			if (i >= 0 && i < forumEntries.size() - 1) {
				if (forumEntries.get(i + 1).getLevel() >
						forumEntry.getLevel()) {
					return true;
				}
			}
			return false;
		}

		private boolean hasVisibleDescendants(ForumEntry forumEntry) {
			int visiblePos = getVisiblePos(forumEntry);
			int levelLimit = forumEntry.getLevel();
			// FIXME This loop doesn't really loop. @ernir please review!
			for (int i = visiblePos + 1; i < getItemCount(); i++) {
				ForumEntry entry = getVisibleEntry(i);
				if (entry != null && entry.getLevel() <= levelLimit)
					break;
				return true;
			}
			return false;
		}

		private int getReplyCount(ForumEntry entry) {
			int counter = 0;
			int pos = forumEntries.indexOf(entry);
			if (pos >= 0) {
				int ancestorLvl = forumEntries.get(pos).getLevel();
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
				if (entry.getMessageId().equals(messageId)) {
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


		@Nullable
		ForumEntry getVisibleEntry(int position) {
			int levelLimit = UNDEFINED;
			for (ForumEntry forumEntry : forumEntries) {
				if (levelLimit >= 0) {
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

		private void animateFadeOut(final ForumViewHolder ui,
				final ForumEntry addedEntry) {
			ui.setIsRecyclable(false);
			ValueAnimator anim = new ValueAnimator();
			animatingEntries.put(addedEntry, anim);
			ColorDrawable viewColor = (ColorDrawable) ui.cell.getBackground();
			anim.setIntValues(viewColor.getColor(), ContextCompat
					.getColor(ForumActivity.this,
							R.color.window_background));
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
		public ForumViewHolder onCreateViewHolder(ViewGroup parent,
				int viewType) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.forum_discussion_cell, parent, false);
			return new ForumViewHolder(v);
		}

		@Override
		public void onBindViewHolder(
				final ForumViewHolder ui, final int position) {
			final ForumEntry data = getVisibleEntry(position);
			if (data == null) return;

			if (!data.isRead()) {
				data.setRead(true);
				forumController.entryRead(data);
			}
			ui.textView.setText(StringUtils.trim(data.getText()));

			if (position == 0) {
				ui.topDivider.setVisibility(View.INVISIBLE);
			} else {
				ui.topDivider.setVisibility(View.VISIBLE);
			}

			for (int i = 0; i < ui.lvls.length; i++) {
				ui.lvls[i].setVisibility(i < data.getLevel() ? VISIBLE : GONE);
			}
			if (data.getLevel() > 5) {
				ui.lvlText.setVisibility(VISIBLE);
				ui.lvlText.setText("" + data.getLevel());
			} else {
				ui.lvlText.setVisibility(GONE);
			}
			ui.authorText.setText(data.getAuthor());
			ui.dateText.setText(AndroidUtils
					.formatDate(ForumActivity.this, data.getTimestamp()));
			ui.trust.setTrustLevel(data.getStatus());

			int replies = getReplyCount(data);
			if (replies == 0) {
				ui.repliesText.setText("");
			} else {
				ui.repliesText.setText(getResources()
						.getQuantityString(R.plurals.message_replies, replies,
								replies));
			}
			ui.avatar.setImageDrawable(
					new IdenticonDrawable(data.getAuthorId().getBytes()));

			if (hasDescendants(data)) {
				ui.chevron.setVisibility(VISIBLE);
				if (hasVisibleDescendants(data)) {
					ui.chevron.setSelected(false);
				} else {
					ui.chevron.setSelected(true);
				}
				ui.chevron.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						ui.chevron.setSelected(!ui.chevron.isSelected());
						if (ui.chevron.isSelected()) {
							hideDescendants(data);
						} else {
							showDescendants(data);
						}
					}
				});
			} else {
				ui.chevron.setVisibility(INVISIBLE);
			}
			if (data.equals(replyEntry)) {
				ui.cell.setBackgroundColor(ContextCompat
						.getColor(ForumActivity.this,
								R.color.forum_cell_highlight));
			} else if (data.equals(addedEntry)) {

				ui.cell.setBackgroundColor(ContextCompat
						.getColor(ForumActivity.this,
								R.color.forum_cell_highlight));
				animateFadeOut(ui, addedEntry);
				addedEntry = null;
			} else {
				ui.cell.setBackgroundColor(ContextCompat
						.getColor(ForumActivity.this,
								R.color.window_background));
			}
			ui.replyButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showTextInput(data);
					linearLayoutManager
							.scrollToPositionWithOffset(getVisiblePos(data), 0);
				}
			});
		}

		private int getVisiblePos(ForumEntry sEntry) {
			int visibleCounter = 0;
			int levelLimit = UNDEFINED;
			for (ForumEntry fEntry : forumEntries) {
				if (levelLimit >= 0) {
					if (fEntry.getLevel() > levelLimit) {
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
	}

}
