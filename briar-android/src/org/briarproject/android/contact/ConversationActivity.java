package org.briarproject.android.contact;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.introduction.IntroductionActivity;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.conversation.ConversationItem.IncomingItem;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;

import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;

public class ConversationActivity extends BriarActivity
		implements ConversationController.ConversationListener, OnClickListener,
		ConversationAdapter.ConversationHandler,
		ConversationAdapter.MessageUpdatedHandler {

	private static final Logger LOG =
			Logger.getLogger(ConversationActivity.class.getName());
	private static final int REQUEST_CODE_INTRODUCTION = 1;

	@Inject
	AndroidNotificationManager notificationManager;

	private ConversationAdapter adapter;
	private CircleImageView toolbarAvatar;
	private ImageView toolbarStatus;
	private TextView toolbarTitle;
	private BriarRecyclerView list;
	private EditText content;
	private View sendButton;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ConversationController conversationController;

	private volatile GroupId groupId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);

		setContentView(R.layout.activity_conversation);

		// Custom Toolbar
		Toolbar tb = (Toolbar) findViewById(R.id.toolbar);
		if (tb != null) {
			toolbarAvatar =
					(CircleImageView) tb.findViewById(R.id.contactAvatar);
			toolbarStatus = (ImageView) tb.findViewById(R.id.contactStatus);
			toolbarTitle = (TextView) tb.findViewById(R.id.contactName);
			setSupportActionBar(tb);
		}
		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayShowHomeEnabled(true);
			ab.setDisplayHomeAsUpEnabled(true);
			ab.setDisplayShowCustomEnabled(true);
			ab.setDisplayShowTitleEnabled(false);
		}

		String hexGroupId = StringUtils.toHexString(b);
		ViewCompat.setTransitionName(toolbarAvatar, "avatar" + hexGroupId);
		ViewCompat.setTransitionName(toolbarStatus, "bulb" + hexGroupId);

		adapter = new ConversationAdapter(this, this, this);
		list = (BriarRecyclerView) findViewById(R.id.conversationView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_private_messages));
		list.periodicallyUpdateContent();

		content = (EditText) findViewById(R.id.input_text);
		sendButton = findViewById(R.id.btn_send);
		if (sendButton != null) {
			// Enabled after loading the conversation
			sendButton.setEnabled(false);
			sendButton.setOnClickListener(this);
		}

		conversationController
				.loadConversation(groupId, new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(Boolean result) {
						if (result) {
							displayContactDetails();
							// Load the messages here to make sure we have a
							// contactId
							loadMessages();
						} else {
							// TODO Maybe an error dialog ?
							finish();
						}
					}
				});
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_CODE_INTRODUCTION && result == RESULT_OK) {
			Snackbar snackbar = Snackbar.make(list, R.string.introduction_sent,
					Snackbar.LENGTH_SHORT);
			snackbar.getView().setBackgroundResource(R.color.briar_primary);
			snackbar.show();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		notificationManager.blockNotification(groupId);
		notificationManager.clearPrivateMessageNotification(groupId);
	}

	@Override
	public void onPause() {
		super.onPause();
		notificationManager.unblockNotification(groupId);
		if (isFinishing()) markMessagesRead();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.conversation_actions, menu);

		final MenuItem introduction = menu.findItem(R.id.action_introduction);
		conversationController.shouldHideIntroductionAction(
				new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(Boolean result) {
						if (result) {
							introduction.setVisible(false);
						}
					}
				});

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			case R.id.action_introduction:
				ContactId contactId = conversationController.getContactId();
				if (contactId == null) return false;
				Intent intent = new Intent(this, IntroductionActivity.class);
				intent.putExtra(IntroductionActivity.CONTACT_ID,
						contactId.getInt());
				ActivityOptionsCompat options =
						makeCustomAnimation(this, android.R.anim.slide_in_left,
								android.R.anim.slide_out_right);
				ActivityCompat.startActivityForResult(this, intent,
						REQUEST_CODE_INTRODUCTION, options.toBundle());
				return true;
			case R.id.action_social_remove_person:
				askToRemoveContact();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		// FIXME disabled exit transition, because it doesn't work for some reason #318
		//supportFinishAfterTransition();
		finish();
	}

	/**
	 * This should only be called after the conversation has been loaded.
	 */
	private void displayContactDetails() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				toolbarAvatar.setImageDrawable(new IdenticonDrawable(
						conversationController.getContactIdenticonKey()));
				toolbarTitle.setText(conversationController.getContactName());

				if (conversationController.isConnected()) {
					toolbarStatus.setImageDrawable(ContextCompat
							.getDrawable(ConversationActivity.this,
									R.drawable.contact_online));
					toolbarStatus
							.setContentDescription(getString(R.string.online));
				} else {
					toolbarStatus.setImageDrawable(ContextCompat
							.getDrawable(ConversationActivity.this,
									R.drawable.contact_offline));
					toolbarStatus
							.setContentDescription(getString(R.string.offline));
				}
				adapter.setContactName(conversationController.getContactName());
			}
		});
	}

	private void loadMessages() {
		conversationController.loadMessages(new UiResultHandler<Boolean>(this) {
			@Override
			public void onResultUi(Boolean result) {
				if (result) {
					List<ConversationItem> items =
							conversationController.getConversationItems();
					sendButton.setEnabled(true);
					if (items.isEmpty()) {
						// we have no messages,
						// so let the list know to hide progress bar
						list.showData();
					} else {
						adapter.addAll(items);
						// Scroll to the bottom
						list.scrollToPosition(adapter.getItemCount() - 1);
					}
				} else {
					finish();
				}
			}
		});
	}

	private void addConversationItem(final ConversationItem item) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.add(item);
				// Scroll to the bottom
				list.scrollToPosition(adapter.getItemCount() - 1);
			}
		});
	}

	private void markMessagesRead() {
		List<ConversationItem> unread = new ArrayList<>();
		SparseArray<IncomingItem> list = adapter.getIncomingMessages();
		for (int i = 0; i < list.size(); i++) {
			IncomingItem item = list.valueAt(i);
			if (!item.isRead()) unread.add(item);
		}
		if (unread.isEmpty()) return;
		if (LOG.isLoggable(INFO))
			LOG.info("Marking " + unread.size() + " messages read");
		conversationController.markMessagesRead(
				Collections.unmodifiableList(unread),
				new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(Boolean result) {
						// TODO something?
					}
				});
	}

	private void markMessageReadIfNew(final ConversationItem item) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ConversationItem last = adapter.getLastItem();
				if (last != null) {
					// Mark the message read if it's the newest message
					long lastMsgTime = last.getTime();
					long newMsgTime = item.getTime();
					if (newMsgTime > lastMsgTime)
						conversationController.markNewMessageRead(item);
				} else {
					// mark the message as read as well if it is the first one
					conversationController.markNewMessageRead(item);
				}
				loadMessages();
			}
		});
	}

	@Override
	public void markMessages(final Collection<MessageId> messageIds,
			final boolean sent, final boolean seen) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Set<MessageId> messages = new HashSet<>(messageIds);
				SparseArray<ConversationItem.OutgoingItem> list =
						adapter.getOutgoingMessages();
				for (int i = 0; i < list.size(); i++) {
					ConversationItem.OutgoingItem item = list.valueAt(i);
					if (messages.contains(item.getId())) {
						item.setSent(sent);
						item.setSeen(seen);
						adapter.notifyItemChanged(list.keyAt(i));
					}
				}
			}
		});
	}

	@Override
	public void messageReceived(ConversationItem item) {
		addConversationItem(item);
		markMessageReadIfNew(item);
	}

	@Override
	public void onClick(View view) {
		markMessagesRead();
		String message = content.getText().toString();
		if (message.equals("")) return;
		long timestamp = System.currentTimeMillis();
		timestamp = Math.max(timestamp, getMinTimestampForNewMessage());
		createMessage(StringUtils.toUtf8(message), timestamp);
		content.setText("");
	}

	private long getMinTimestampForNewMessage() {
		// Don't use an earlier timestamp than the newest message
		ConversationItem item = adapter.getLastItem();
		return item == null ? 0 : item.getTime() + 1;
	}

	private void createMessage(final byte[] body, final long timestamp) {
		conversationController.createMessage(body, timestamp,
				new UiResultHandler<ConversationItem>(this) {
					@Override
					public void onResultUi(ConversationItem item) {
						if (item != null)
							addConversationItem(item);
					}
				});
	}

	private void askToRemoveContact() {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeContact();
					}
				};
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ConversationActivity.this,
						R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_delete_contact));
		builder.setMessage(getString(R.string.dialog_message_delete_contact));
		builder.setPositiveButton(android.R.string.ok, okListener);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void removeContact() {
		conversationController
				.removeContact(new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(Boolean result) {
						if (result) {
							String deleted =
									getString(R.string.contact_deleted_toast);
							Toast.makeText(ConversationActivity.this, deleted,
									LENGTH_SHORT)
									.show();
							finish();
						} else {
							String failed = getString(
									R.string.contact_deletion_failed_toast);
							Toast.makeText(ConversationActivity.this, failed,
									LENGTH_SHORT).show();
						}
					}
				});
	}

	@Override
	public void respondToItem(ConversationItem item, boolean accept) {
		long minTimestamp = getMinTimestampForNewMessage();
		conversationController.respondToItem(item, accept, minTimestamp,
				new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(Boolean result) {
						if (result) {
							loadMessages();
						} else {
							// TODO decide how to make this type-agnostic
							introductionResponseError();
						}
					}
				});
	}

	private void introductionResponseError() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(ConversationActivity.this,
						R.string.introduction_response_error,
						Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void messageUpdated(final int position) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.notifyItemChanged(position);
				// Scroll to the bottom
				list.scrollToPosition(adapter.getItemCount() - 1);
			}
		});
	}

	@Override
	public void contactUpdated() {
		displayContactDetails();
	}
}
