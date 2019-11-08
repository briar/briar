package org.briarproject.briar.android.conversation;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.transition.Slide;
import android.transition.Transition;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.event.MessagesAckedEvent;
import org.briarproject.bramble.api.sync.event.MessagesSentEvent;
import org.briarproject.bramble.api.versioning.event.ClientVersionUpdatedEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.attachment.AttachmentItem;
import org.briarproject.briar.android.attachment.AttachmentRetriever;
import org.briarproject.briar.android.blog.BlogActivity;
import org.briarproject.briar.android.conversation.ConversationVisitor.AttachmentCache;
import org.briarproject.briar.android.conversation.ConversationVisitor.TextCache;
import org.briarproject.briar.android.forum.ForumActivity;
import org.briarproject.briar.android.introduction.IntroductionActivity;
import org.briarproject.briar.android.privategroup.conversation.GroupActivity;
import org.briarproject.briar.android.util.BriarSnackbarBuilder;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.android.view.ImagePreview;
import org.briarproject.briar.android.view.TextAttachmentController;
import org.briarproject.briar.android.view.TextAttachmentController.AttachmentListener;
import org.briarproject.briar.android.view.TextInputView;
import org.briarproject.briar.android.view.TextSendController;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.ConversationRequest;
import org.briarproject.briar.api.conversation.ConversationResponse;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.messaging.event.AttachmentReceivedEvent;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.SelectionTracker.SelectionObserver;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

import static android.os.Build.VERSION.SDK_INT;
import static android.view.Gravity.RIGHT;
import static android.widget.Toast.LENGTH_SHORT;
import static androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation;
import static androidx.core.view.ViewCompat.setTransitionName;
import static androidx.lifecycle.Lifecycle.State.STARTED;
import static androidx.recyclerview.widget.SortedList.INVALID_POSITION;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.sort;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_ATTACH_IMAGE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_INTRODUCTION;
import static org.briarproject.briar.android.conversation.ImageActivity.ATTACHMENTS;
import static org.briarproject.briar.android.conversation.ImageActivity.ATTACHMENT_POSITION;
import static org.briarproject.briar.android.conversation.ImageActivity.DATE;
import static org.briarproject.briar.android.conversation.ImageActivity.NAME;
import static org.briarproject.briar.android.util.UiUtils.getAvatarTransitionName;
import static org.briarproject.briar.android.util.UiUtils.getBulbTransitionName;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_ATTACHMENTS_PER_MESSAGE;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_TEXT_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ConversationActivity extends BriarActivity
		implements EventListener, ConversationListener, TextCache,
		AttachmentCache, AttachmentListener, ActionMode.Callback {

	public static final String CONTACT_ID = "briar.CONTACT_ID";

	private static final Logger LOG =
			getLogger(ConversationActivity.class.getName());

	private static final int TRANSITION_DURATION_MS = 500;
	private static final int ONBOARDING_DELAY_MS = 250;

	@Inject
	AndroidNotificationManager notificationManager;
	@Inject
	ConnectionRegistry connectionRegistry;
	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	FeatureFlags featureFlags;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactManager contactManager;
	@Inject
	volatile MessagingManager messagingManager;
	@Inject
	volatile ConversationManager conversationManager;
	@Inject
	volatile EventBus eventBus;
	@Inject
	volatile IntroductionManager introductionManager;
	@Inject
	volatile ForumSharingManager forumSharingManager;
	@Inject
	volatile BlogSharingManager blogSharingManager;
	@Inject
	volatile GroupInvitationManager groupInvitationManager;

	private final Map<MessageId, String> textCache = new ConcurrentHashMap<>();
	private final Map<MessageId, PrivateMessageHeader> missingAttachments =
			new ConcurrentHashMap<>();
	private final Observer<String> contactNameObserver = name -> {
		requireNonNull(name);
		loadMessages();
	};

	private AttachmentRetriever attachmentRetriever;
	private ConversationViewModel viewModel;
	private ConversationVisitor visitor;
	private ConversationAdapter adapter;
	private Toolbar toolbar;
	private CircleImageView toolbarAvatar;
	private ImageView toolbarStatus;
	private TextView toolbarTitle;
	private BriarRecyclerView list;
	private LinearLayoutManager layoutManager;
	private TextInputView textInputView;
	private TextSendController sendController;
	private SelectionTracker<String> tracker;
	@Nullable
	private Parcelable layoutManagerState;
	@Nullable
	private ActionMode actionMode;

	private volatile ContactId contactId;

	@Override
	public void onCreate(@Nullable Bundle state) {
		if (SDK_INT >= 21) {
			// Spurious lint warning - using END causes a crash
			@SuppressLint("RtlHardcoded")
			Transition slide = new Slide(RIGHT);
			slide.setDuration(TRANSITION_DURATION_MS);
			setSceneTransitionAnimation(slide, null, slide);
		}
		super.onCreate(state);

		Intent i = getIntent();
		int id = i.getIntExtra(CONTACT_ID, -1);
		if (id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);

		viewModel = ViewModelProviders.of(this, viewModelFactory)
				.get(ConversationViewModel.class);
		attachmentRetriever = viewModel.getAttachmentRetriever();

		setContentView(R.layout.activity_conversation);

		// Custom Toolbar
		toolbar = requireNonNull(setUpCustomToolbar(true));
		toolbarAvatar = toolbar.findViewById(R.id.contactAvatar);
		toolbarStatus = toolbar.findViewById(R.id.contactStatus);
		toolbarTitle = toolbar.findViewById(R.id.contactName);

		observeOnce(viewModel.getContactAuthorId(), this, authorId -> {
			requireNonNull(authorId);
			toolbarAvatar.setImageDrawable(
					new IdenticonDrawable(authorId.getBytes()));
		});
		viewModel.getContactDisplayName().observe(this, contactName -> {
			requireNonNull(contactName);
			toolbarTitle.setText(contactName);
		});
		viewModel.isContactDeleted().observe(this, deleted -> {
			requireNonNull(deleted);
			if (deleted) finish();
		});
		viewModel.getAddedPrivateMessage().observeEvent(this,
				this::onAddedPrivateMessage);

		setTransitionName(toolbarAvatar, getAvatarTransitionName(contactId));
		setTransitionName(toolbarStatus, getBulbTransitionName(contactId));

		visitor = new ConversationVisitor(this, this, this,
				viewModel.getContactDisplayName());
		adapter = new ConversationAdapter(this, this);
		list = findViewById(R.id.conversationView);
		layoutManager = new LinearLayoutManager(this);
		list.setLayoutManager(layoutManager);
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_private_messages));
		ConversationScrollListener scrollListener =
				new ConversationScrollListener(adapter, viewModel);
		list.getRecyclerView().addOnScrollListener(scrollListener);
		if (featureFlags.shouldEnablePrivateMessageDeletion()) {
			addSelectionTracker();
		}

		textInputView = findViewById(R.id.text_input_container);
		if (featureFlags.shouldEnableImageAttachments()) {
			ImagePreview imagePreview = findViewById(R.id.imagePreview);
			sendController = new TextAttachmentController(textInputView,
					imagePreview, this, viewModel);
			viewModel.hasImageSupport().observe(this, new Observer<Boolean>() {
				@Override
				public void onChanged(@Nullable Boolean hasSupport) {
					if (hasSupport != null && hasSupport) {
						// TODO: remove cast when removing feature flag
						((TextAttachmentController) sendController)
								.setImagesSupported();
						viewModel.hasImageSupport().removeObserver(this);
					}
				}
			});
		} else {
			sendController = new TextSendController(textInputView, this, false);
		}
		textInputView.setSendController(sendController);
		textInputView.setMaxTextLength(MAX_PRIVATE_MESSAGE_TEXT_LENGTH);
		textInputView.setReady(false);
		textInputView.setOnKeyboardShownListener(this::scrollToBottom);
	}

	private void scrollToBottom() {
		int items = adapter.getItemCount();
		if (items > 0) list.scrollToPosition(items - 1);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_INTRODUCTION && result == RESULT_OK) {
			new BriarSnackbarBuilder()
					.make(list, R.string.introduction_sent,
							Snackbar.LENGTH_SHORT)
					.show();
		} else if (request == REQUEST_ATTACH_IMAGE && result == RESULT_OK) {
			// TODO: remove cast when removing feature flag
			((TextAttachmentController) sendController).onImageReceived(data);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
		notificationManager.blockContactNotification(contactId);
		notificationManager.clearContactNotification(contactId);
		displayContactOnlineStatus();
		viewModel.getContactDisplayName().observe(this, contactNameObserver);
		list.startPeriodicUpdate();
	}

	@Override
	public void onResume() {
		super.onResume();
		// Trigger loading of contact data, noop if data was loaded already.
		//
		// We can only start loading data *after* we are sure
		// the user has signed in. After sign-in, onCreate() isn't run again.
		if (signedIn()) viewModel.setContactId(contactId);
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
		notificationManager.unblockContactNotification(contactId);
		viewModel.getContactDisplayName().removeObserver(contactNameObserver);
		list.stopPeriodicUpdate();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (layoutManager != null) {
			layoutManagerState = layoutManager.onSaveInstanceState();
			outState.putParcelable("layoutManager", layoutManagerState);
		}
		if (tracker != null) tracker.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		layoutManagerState = savedInstanceState.getParcelable("layoutManager");
		if (tracker != null) tracker.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.conversation_actions, menu);

		// Hide private message deletion action if feature is not enabled
		if (!featureFlags.shouldEnablePrivateMessageDeletion()) {
			menu.removeItem(R.id.action_delete_all_messages);
		}

		// enable introduction action if available
		observeOnce(viewModel.showIntroductionAction(), this, enable -> {
			if (enable != null && enable) {
				menu.findItem(R.id.action_introduction).setEnabled(true);
				// show introduction onboarding, if needed
				viewModel.showIntroductionOnboarding().observeEvent(this,
						this::showIntroductionOnboarding);
			}
		});
		// enable alias action if available
		observeOnce(viewModel.getContact(), this, contact ->
				menu.findItem(R.id.action_set_alias).setEnabled(true));

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			case R.id.action_introduction:
				if (contactId == null) return false;
				Intent intent = new Intent(this, IntroductionActivity.class);
				intent.putExtra(CONTACT_ID, contactId.getInt());
				startActivityForResult(intent, REQUEST_INTRODUCTION);
				return true;
			case R.id.action_set_alias:
				AliasDialogFragment.newInstance().show(
						getSupportFragmentManager(), AliasDialogFragment.TAG);
				return true;
			case R.id.action_delete_all_messages:
				askToDeleteAllMessages();
				return true;
			case R.id.action_social_remove_person:
				askToRemoveContact();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		MenuInflater inflater = mode.getMenuInflater();
		inflater.inflate(R.menu.conversation_message_actions, menu);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false; // no update needed
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		if (item.getItemId() == R.id.action_delete) {
			deleteSelectedMessages();
			return true;
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		tracker.clearSelection();
		actionMode = null;
	}

	private void addSelectionTracker() {
		RecyclerView recyclerView = list.getRecyclerView();
		if (recyclerView.getAdapter() != adapter)
			throw new IllegalStateException();

		tracker = new SelectionTracker.Builder<>(
				"conversationSelection",
				recyclerView,
				new ConversationItemKeyProvider(adapter),
				new ConversationItemDetailsLookup(recyclerView),
				StorageStrategy.createStringStorage()
		).withSelectionPredicate(
				SelectionPredicates.createSelectAnything()
		).build();

		SelectionObserver<String> observer = new SelectionObserver<String>() {
			@Override
			public void onItemStateChanged(String key, boolean selected) {
				if (selected && actionMode == null) {
					actionMode = startActionMode(ConversationActivity.this);
					updateActionModeTitle();
				} else if (actionMode != null) {
					if (selected || tracker.hasSelection()) {
						updateActionModeTitle();
					} else {
						actionMode.finish();
					}
				}
			}
		};
		tracker.addObserver(observer);
		adapter.setSelectionTracker(tracker);
	}

	private void updateActionModeTitle() {
		if (actionMode == null) throw new IllegalStateException();
		String title = String.valueOf(tracker.getSelection().size());
		actionMode.setTitle(title);
	}

	private Collection<MessageId> getSelection() {
		Selection<String> selection = tracker.getSelection();
		List<MessageId> messages = new ArrayList<>(selection.size());
		for (String str : selection) {
			MessageId id = new MessageId(fromHexString(str));
			messages.add(id);
		}
		return messages;
	}

	@UiThread
	private void displayContactOnlineStatus() {
		if (connectionRegistry.isConnected(contactId)) {
			toolbarStatus.setImageDrawable(ContextCompat.getDrawable(
					ConversationActivity.this, R.drawable.contact_online));
			toolbarStatus.setContentDescription(getString(R.string.online));
		} else {
			toolbarStatus.setImageDrawable(ContextCompat.getDrawable(
					ConversationActivity.this, R.drawable.contact_offline));
			toolbarStatus.setContentDescription(getString(R.string.offline));
		}
	}

	private void loadMessages() {
		int revision = adapter.getRevision();
		runOnDbThread(() -> {
			try {
				long start = now();
				Collection<ConversationMessageHeader> headers =
						conversationManager.getMessageHeaders(contactId);
				logDuration(LOG, "Loading messages", start);
				// Sort headers by timestamp in *descending* order
				List<ConversationMessageHeader> sorted =
						new ArrayList<>(headers);
				sort(sorted, (a, b) ->
						Long.compare(b.getTimestamp(), a.getTimestamp()));
				if (!sorted.isEmpty()) {
					// If the latest header is a private message, eagerly load
					// its size so we can set the scroll position correctly
					ConversationMessageHeader latest = sorted.get(0);
					if (latest instanceof PrivateMessageHeader) {
						eagerlyLoadMessageSize((PrivateMessageHeader) latest);
					}
				}
				displayMessages(revision, sorted);
			} catch (NoSuchContactException e) {
				finishOnUiThread();
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void eagerlyLoadMessageSize(PrivateMessageHeader h) {
		try {
			MessageId id = h.getId();
			// If the message has text, load it
			if (h.hasText()) {
				String text = textCache.get(id);
				if (text == null) {
					LOG.info("Eagerly loading text for latest message");
					text = messagingManager.getMessageText(id);
					textCache.put(id, requireNonNull(text));
				}
			}
			// If the message has a single image, load its size - for multiple
			// images we use a grid so the size is fixed
			List<AttachmentHeader> headers = h.getAttachmentHeaders();
			if (headers.size() == 1) {
				List<AttachmentItem> items = attachmentRetriever.cacheGet(id);
				if (items == null) {
					LOG.info("Eagerly loading image size for latest message");
					AttachmentHeader header = headers.get(0);
					try {
						Attachment a = attachmentRetriever
								.getMessageAttachment(header);
						AttachmentItem item =
								attachmentRetriever.getAttachmentItem(a, true);
						attachmentRetriever.cachePut(id, singletonList(item));
					} catch (NoSuchMessageException e) {
						LOG.info("Attachment not received yet");
						missingAttachments.put(header.getMessageId(), h);
					}
				}
			}
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void displayMessages(int revision,
			Collection<ConversationMessageHeader> headers) {
		runOnUiThreadUnlessDestroyed(() -> {
			if (revision == adapter.getRevision()) {
				adapter.incrementRevision();
				textInputView.setReady(true);
				// start observing onboarding after enabling
				if (featureFlags.shouldEnableImageAttachments()) {
					viewModel.showImageOnboarding().observeEvent(this,
							this::showImageOnboarding);
				}
				List<ConversationItem> items = createItems(headers);
				adapter.addAll(items);
				list.showData();
				if (layoutManagerState == null) {
					scrollToBottom();
				} else {
					// Restore the previous scroll position
					layoutManager.onRestoreInstanceState(layoutManagerState);
				}
			} else {
				LOG.info("Concurrent update, reloading");
				loadMessages();
			}
		});
	}

	/**
	 * Creates ConversationItems from headers loaded from the database.
	 * <p>
	 * Attention: Call this only after contactName has been initialized.
	 */
	private List<ConversationItem> createItems(
			Collection<ConversationMessageHeader> headers) {
		List<ConversationItem> items = new ArrayList<>(headers.size());
		for (ConversationMessageHeader h : headers)
			items.add(h.accept(visitor));
		return items;
	}

	private void loadMessageText(MessageId m) {
		runOnDbThread(() -> {
			try {
				long start = now();
				String text = messagingManager.getMessageText(m);
				logDuration(LOG, "Loading text", start);
				displayMessageText(m, requireNonNull(text));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void displayMessageText(MessageId m, String text) {
		runOnUiThreadUnlessDestroyed(() -> {
			textCache.put(m, text);
			Pair<Integer, ConversationMessageItem> pair =
					adapter.getMessageItem(m);
			if (pair != null) {
				boolean scroll = shouldScrollWhenUpdatingMessage();
				pair.getSecond().setText(text);
				adapter.notifyItemChanged(pair.getFirst());
				if (scroll) scrollToBottom();
			}
		});
	}

	// When a message's text or attachments are loaded, scroll to the bottom
	// if the conversation is visible and we were previously at the bottom
	private boolean shouldScrollWhenUpdatingMessage() {
		return getLifecycle().getCurrentState().isAtLeast(STARTED)
				&& adapter.isScrolledToBottom(layoutManager);
	}

	private void loadMessageAttachments(PrivateMessageHeader h) {
		// TODO: Use placeholders for missing/invalid attachments
		runOnDbThread(() -> {
			try {
				// TODO move getting the items off to IoExecutor, if size == 1
				List<AttachmentHeader> headers = h.getAttachmentHeaders();
				boolean needsSize = headers.size() == 1;
				List<AttachmentItem> items = new ArrayList<>(headers.size());
				for (AttachmentHeader header : headers) {
					try {
						Attachment a = attachmentRetriever
								.getMessageAttachment(header);
						AttachmentItem item = attachmentRetriever
								.getAttachmentItem(a, needsSize);
						items.add(item);
					} catch (NoSuchMessageException e) {
						LOG.info("Attachment not received yet");
						missingAttachments.put(header.getMessageId(), h);
						return;
					}
				}
				// Don't cache items unless all are present and valid
				attachmentRetriever.cachePut(h.getId(), items);
				displayMessageAttachments(h.getId(), items);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void displayMessageAttachments(MessageId m,
			List<AttachmentItem> items) {
		runOnUiThreadUnlessDestroyed(() -> {
			Pair<Integer, ConversationMessageItem> pair =
					adapter.getMessageItem(m);
			if (pair != null) {
				boolean scroll = shouldScrollWhenUpdatingMessage();
				pair.getSecond().setAttachments(items);
				adapter.notifyItemChanged(pair.getFirst());
				if (scroll) scrollToBottom();
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof AttachmentReceivedEvent) {
			AttachmentReceivedEvent a = (AttachmentReceivedEvent) e;
			if (a.getContactId().equals(contactId)) {
				LOG.info("Attachment received");
				onAttachmentReceived(a.getMessageId());
			}
		}
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact removed");
				supportFinishAfterTransition();
			}
		} else if (e instanceof ConversationMessageReceivedEvent) {
			ConversationMessageReceivedEvent p =
					(ConversationMessageReceivedEvent) e;
			if (p.getContactId().equals(contactId)) {
				LOG.info("Message received, adding");
				onNewConversationMessage(p.getMessageHeader());
			}
		} else if (e instanceof MessagesSentEvent) {
			MessagesSentEvent m = (MessagesSentEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages sent");
				markMessages(m.getMessageIds(), true, false);
			}
		} else if (e instanceof MessagesAckedEvent) {
			MessagesAckedEvent m = (MessagesAckedEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages acked");
				markMessages(m.getMessageIds(), true, true);
			}
		} else if (e instanceof ContactConnectedEvent) {
			ContactConnectedEvent c = (ContactConnectedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact connected");
				displayContactOnlineStatus();
			}
		} else if (e instanceof ContactDisconnectedEvent) {
			ContactDisconnectedEvent c = (ContactDisconnectedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact disconnected");
				displayContactOnlineStatus();
			}
		} else if (e instanceof ClientVersionUpdatedEvent) {
			ClientVersionUpdatedEvent c = (ClientVersionUpdatedEvent) e;
			if (c.getContactId().equals(contactId)) {
				ClientId clientId = c.getClientVersion().getClientId();
				if (clientId.equals(MessagingManager.CLIENT_ID)) {
					LOG.info("Contact's messaging client was updated");
					viewModel.recheckFeaturesAndOnboarding(contactId);
				}
			}
		}
	}

	@UiThread
	private void addConversationItem(ConversationItem item) {
		adapter.incrementRevision();
		adapter.add(item);
		// When adding a new message, scroll to the bottom if the conversation
		// is visible, even if we're not currently at the bottom
		if (getLifecycle().getCurrentState().isAtLeast(STARTED))
			scrollToBottom();
	}

	@UiThread
	private void onAttachmentReceived(MessageId attachmentId) {
		PrivateMessageHeader h = missingAttachments.remove(attachmentId);
		if (h != null) {
			LOG.info("Missing attachment received");
			loadMessageAttachments(h);
		}
	}

	@UiThread
	private void onNewConversationMessage(ConversationMessageHeader h) {
		if (h instanceof ConversationRequest ||
				h instanceof ConversationResponse) {
			// contact name might not have been loaded
			observeOnce(viewModel.getContactDisplayName(), this,
					name -> addConversationItem(h.accept(visitor)));
		} else {
			// visitor also loads message text (if existing)
			addConversationItem(h.accept(visitor));
		}
	}

	@UiThread
	private void markMessages(Collection<MessageId> messageIds, boolean sent,
			boolean seen) {
		adapter.incrementRevision();
		Set<MessageId> messages = new HashSet<>(messageIds);
		SparseArray<ConversationItem> list = adapter.getOutgoingMessages();
		for (int i = 0; i < list.size(); i++) {
			ConversationItem item = list.valueAt(i);
			if (messages.contains(item.getId())) {
				item.setSent(sent);
				item.setSeen(seen);
				adapter.notifyItemChanged(list.keyAt(i));
			}
		}
	}

	@Override
	public void onAttachImage(Intent intent) {
		startActivityForResult(intent, REQUEST_ATTACH_IMAGE);
	}

	@Override
	public void onTooManyAttachments() {
		String format = getResources().getString(
				R.string.messaging_too_many_attachments_toast);
		String warning = String.format(format, MAX_ATTACHMENTS_PER_MESSAGE);
		Toast.makeText(this, warning, LENGTH_SHORT).show();
	}

	@Override
	public void onSendClick(@Nullable String text,
			List<AttachmentHeader> attachmentHeaders) {
		if (isNullOrEmpty(text) && attachmentHeaders.isEmpty())
			throw new AssertionError();
		long timestamp = System.currentTimeMillis();
		timestamp = Math.max(timestamp, getMinTimestampForNewMessage());
		viewModel.sendMessage(text, attachmentHeaders, timestamp);
		textInputView.clearText();
	}

	private long getMinTimestampForNewMessage() {
		// Don't use an earlier timestamp than the newest message
		ConversationItem item = adapter.getLastItem();
		return item == null ? 0 : item.getTime() + 1;
	}

	private void onAddedPrivateMessage(@Nullable PrivateMessageHeader h) {
		if (h == null) return;
		addConversationItem(h.accept(visitor));
	}

	private void askToDeleteAllMessages() {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_delete_all_messages));
		builder.setMessage(
				getString(R.string.dialog_message_delete_all_messages));
		builder.setNegativeButton(R.string.delete,
				(dialog, which) -> deleteAllMessages());
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void deleteAllMessages() {
		list.showProgressBar();
		runOnDbThread(() -> {
			try {
				boolean allDeleted =
						conversationManager.deleteAllMessages(contactId);
				reloadConversationAfterDeletingMessages(allDeleted);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				runOnUiThreadUnlessDestroyed(() -> list.showData());
			}
		});
	}

	private void deleteSelectedMessages() {
		list.showProgressBar();
		Collection<MessageId> selected = getSelection();
		// close action mode only after getting the selection
		if (actionMode != null) actionMode.finish();
		runOnDbThread(() -> {
			try {
				boolean allDeleted =
						conversationManager.deleteMessages(contactId, selected);
				reloadConversationAfterDeletingMessages(allDeleted);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				runOnUiThreadUnlessDestroyed(() -> list.showData());
			}
		});
	}

	private void reloadConversationAfterDeletingMessages(
			boolean allDeleted) {
		runOnUiThreadUnlessDestroyed(() -> {
			adapter.clear();
			list.showProgressBar();  // otherwise clearing shows empty state
			loadMessages();
			if (!allDeleted) showNotAllDeletedDialog();
		});
	}

	private void showNotAllDeletedDialog() {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(
				getString(R.string.dialog_title_not_all_messages_deleted));
		builder.setMessage(
				getString(R.string.dialog_message_not_all_messages_deleted));
		builder.setPositiveButton(R.string.ok, null);
		builder.show();
	}

	private void askToRemoveContact() {
		DialogInterface.OnClickListener okListener =
				(dialog, which) -> removeContact();
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ConversationActivity.this,
						R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_delete_contact));
		builder.setMessage(getString(R.string.dialog_message_delete_contact));
		builder.setNegativeButton(R.string.delete, okListener);
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void removeContact() {
		runOnDbThread(() -> {
			try {
				contactManager.removeContact(contactId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			} finally {
				finishAfterContactRemoved();
			}
		});
	}

	private void finishAfterContactRemoved() {
		runOnUiThreadUnlessDestroyed(() -> {
			String deleted = getString(R.string.contact_deleted_toast);
			Toast.makeText(ConversationActivity.this, deleted, LENGTH_SHORT)
					.show();
			supportFinishAfterTransition();
		});
	}

	private void showImageOnboarding(Boolean show) {
		if (!show) return;
		if (SDK_INT >= 21) {
			// show onboarding only after the enter transition has ended
			// otherwise the tap target animation won't play
			textInputView.postDelayed(this::showImageOnboarding,
					TRANSITION_DURATION_MS + ONBOARDING_DELAY_MS);
		} else {
			showImageOnboarding();
		}
	}

	private void showImageOnboarding() {
		// TODO: remove cast when removing feature flag
		((TextAttachmentController) sendController).showImageOnboarding(this);
	}

	private void showIntroductionOnboarding(@Nullable Boolean show) {
		if (show == null || !show) return;
		if (SDK_INT >= 21) {
			// show onboarding only after the enter transition has ended
			// otherwise the tap target animation won't play
			textInputView.postDelayed(this::showIntroductionOnboarding,
					TRANSITION_DURATION_MS + ONBOARDING_DELAY_MS);
		} else {
			showIntroductionOnboarding();
		}
	}

	private void showIntroductionOnboarding() {
		// find view of overflow icon
		View target = null;
		for (int i = 0; i < toolbar.getChildCount(); i++) {
			if (toolbar.getChildAt(i) instanceof ActionMenuView) {
				ActionMenuView menu = (ActionMenuView) toolbar.getChildAt(i);
				// The overflow icon should be the last child of the menu
				target = menu.getChildAt(menu.getChildCount() - 1);
				// If the menu hasn't been populated yet, use the menu itself
				// as the target
				if (target == null) target = menu;
				break;
			}
		}
		if (target == null) {
			LOG.warning("No Overflow Icon found!");
			return;
		}

		new MaterialTapTargetPrompt.Builder(ConversationActivity.this,
				R.style.OnboardingDialogTheme).setTarget(target)
				.setPrimaryText(R.string.introduction_onboarding_title)
				.setSecondaryText(R.string.introduction_onboarding_text)
				.setIcon(R.drawable.ic_more_vert_accent)
				.setBackgroundColour(
						ContextCompat.getColor(this, R.color.briar_primary))
				.show();
	}

	@UiThread
	@Override
	public void respondToRequest(ConversationRequestItem item, boolean accept) {
		item.setAnswered();
		int position = adapter.findItemPosition(item);
		if (position != INVALID_POSITION) {
			adapter.notifyItemChanged(position, item);
		}
		runOnDbThread(() -> {
			long timestamp = System.currentTimeMillis();
			timestamp = Math.max(timestamp, getMinTimestampForNewMessage());
			try {
				switch (item.getRequestType()) {
					case INTRODUCTION:
						respondToIntroductionRequest(item.getSessionId(),
								accept, timestamp);
						break;
					case FORUM:
						respondToForumRequest(item.getSessionId(), accept);
						break;
					case BLOG:
						respondToBlogRequest(item.getSessionId(), accept);
						break;
					case GROUP:
						respondToGroupRequest(item.getSessionId(), accept);
						break;
					default:
						throw new IllegalArgumentException(
								"Unknown Request Type");
				}
				loadMessages();
			} catch (ProtocolStateException e) {
				// Action is no longer valid - reloading should solve the issue
				logException(LOG, INFO, e);
			} catch (DbException e) {
				// TODO show an error message
				logException(LOG, WARNING, e);
			}
		});
	}

	@UiThread
	@Override
	public void openRequestedShareable(ConversationRequestItem item) {
		if (item.getRequestedGroupId() == null)
			throw new IllegalArgumentException();
		Intent i;
		switch (item.getRequestType()) {
			case FORUM:
				i = new Intent(this, ForumActivity.class);
				break;
			case BLOG:
				i = new Intent(this, BlogActivity.class);
				break;
			case GROUP:
				i = new Intent(this, GroupActivity.class);
				break;
			default:
				throw new IllegalArgumentException("Unknown Request Type");
		}
		i.putExtra(GROUP_ID, item.getRequestedGroupId().getBytes());
		startActivity(i);
	}

	@Override
	public void onAttachmentClicked(View view,
			ConversationMessageItem messageItem, AttachmentItem item) {
		String name;
		if (messageItem.isIncoming()) {
			// must be available when items are being displayed
			name = viewModel.getContactDisplayName().getValue();
		} else {
			name = getString(R.string.you);
		}
		ArrayList<AttachmentItem> attachments =
				new ArrayList<>(messageItem.getAttachments());
		Intent i = new Intent(this, ImageActivity.class);
		i.putParcelableArrayListExtra(ATTACHMENTS, attachments);
		i.putExtra(ATTACHMENT_POSITION, attachments.indexOf(item));
		i.putExtra(NAME, name);
		i.putExtra(DATE, messageItem.getTime());
		// restoring list position should not trigger android bug #224270
		String transitionName = item.getTransitionName();
		ActivityOptionsCompat options =
				makeSceneTransitionAnimation(this, view, transitionName);
		ActivityCompat.startActivity(this, i, options.toBundle());
	}

	@DatabaseExecutor
	private void respondToIntroductionRequest(SessionId sessionId,
			boolean accept, long time) throws DbException {
		introductionManager.respondToIntroduction(contactId, sessionId, time,
				accept);
	}

	@DatabaseExecutor
	private void respondToForumRequest(SessionId id, boolean accept)
			throws DbException {
		forumSharingManager.respondToInvitation(contactId, id, accept);
	}

	@DatabaseExecutor
	private void respondToBlogRequest(SessionId id, boolean accept)
			throws DbException {
		blogSharingManager.respondToInvitation(contactId, id, accept);
	}

	@DatabaseExecutor
	private void respondToGroupRequest(SessionId id, boolean accept)
			throws DbException {
		groupInvitationManager.respondToInvitation(contactId, id, accept);
	}

	@Nullable
	@Override
	public String getText(MessageId m) {
		String text = textCache.get(m);
		if (text == null) loadMessageText(m);
		return text;
	}

	@Override
	public List<AttachmentItem> getAttachmentItems(PrivateMessageHeader h) {
		List<AttachmentItem> attachments =
				attachmentRetriever.cacheGet(h.getId());
		if (attachments == null) {
			loadMessageAttachments(h);
			return emptyList();
		}
		return attachments;
	}

}
