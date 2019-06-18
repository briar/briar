package org.briarproject.briar.android.conversation;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.attachment.AttachmentCreator;
import org.briarproject.briar.android.attachment.AttachmentManager;
import org.briarproject.briar.android.attachment.AttachmentResult;
import org.briarproject.briar.android.attachment.AttachmentRetriever;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.android.attachment.AttachmentDimensions.getAttachmentDimensions;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;
import static org.briarproject.briar.android.util.UiUtils.observeForeverOnce;

@NotNullByDefault
public class ConversationViewModel extends AndroidViewModel
		implements AttachmentManager {

	private static Logger LOG =
			getLogger(ConversationViewModel.class.getName());

	private static final String SHOW_ONBOARDING_IMAGE =
			"showOnboardingImage";
	private static final String SHOW_ONBOARDING_INTRODUCTION =
			"showOnboardingIntroduction";

	@DatabaseExecutor
	private final Executor dbExecutor;
	private final TransactionManager db;
	private final MessagingManager messagingManager;
	private final ContactManager contactManager;
	private final SettingsManager settingsManager;
	private final PrivateMessageFactory privateMessageFactory;
	private final AttachmentRetriever attachmentRetriever;
	private final AttachmentCreator attachmentCreator;

	@Nullable
	private ContactId contactId = null;
	private final MutableLiveData<Contact> contact = new MutableLiveData<>();
	private final LiveData<AuthorId> contactAuthorId =
			Transformations.map(contact, c -> c.getAuthor().getId());
	private final LiveData<String> contactName =
			Transformations.map(contact, UiUtils::getContactDisplayName);
	private final LiveData<GroupId> messagingGroupId;
	private final MutableLiveData<Boolean> imageSupport =
			new MutableLiveData<>();
	private final MutableLiveEvent<Boolean> showImageOnboarding =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> showIntroductionOnboarding =
			new MutableLiveEvent<>();
	private final MutableLiveData<Boolean> showIntroductionAction =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> contactDeleted =
			new MutableLiveData<>();
	private final MutableLiveEvent<PrivateMessageHeader> addedHeader =
			new MutableLiveEvent<>();

	@Inject
	ConversationViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			@IoExecutor Executor ioExecutor, TransactionManager db,
			MessagingManager messagingManager, ContactManager contactManager,
			SettingsManager settingsManager,
			PrivateMessageFactory privateMessageFactory) {
		super(application);
		this.dbExecutor = dbExecutor;
		this.db = db;
		this.messagingManager = messagingManager;
		this.contactManager = contactManager;
		this.settingsManager = settingsManager;
		this.privateMessageFactory = privateMessageFactory;
		this.attachmentRetriever = new AttachmentRetriever(messagingManager,
				getAttachmentDimensions(application.getResources()));
		this.attachmentCreator = new AttachmentCreator(getApplication(),
				ioExecutor, messagingManager, attachmentRetriever);
		messagingGroupId = Transformations
				.map(contact, c -> messagingManager.getContactGroup(c).getId());
		contactDeleted.setValue(false);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		attachmentCreator.deleteUnsentAttachments();
	}

	/**
	 * Setting the {@link ContactId} automatically triggers loading of other
	 * data.
	 */
	void setContactId(ContactId contactId) {
		if (this.contactId == null) {
			this.contactId = contactId;
			loadContact(contactId);
		} else if (!contactId.equals(this.contactId)) {
			throw new IllegalStateException();
		}
	}

	private void loadContact(ContactId contactId) {
		dbExecutor.execute(() -> {
			try {
				long start = now();
				Contact c = contactManager.getContact(contactId);
				contact.postValue(c);
				logDuration(LOG, "Loading contact", start);
				start = now();
				checkFeaturesAndOnboarding(contactId);
				logDuration(LOG, "Checking for image support", start);
			} catch (NoSuchContactException e) {
				contactDeleted.postValue(true);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	void markMessageRead(GroupId g, MessageId m) {
		dbExecutor.execute(() -> {
			try {
				long start = now();
				messagingManager.setReadFlag(g, m, true);
				logDuration(LOG, "Marking read", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	void setContactAlias(String alias) {
		dbExecutor.execute(() -> {
			try {
				contactManager.setContactAlias(requireNonNull(contactId),
						alias.isEmpty() ? null : alias);
				loadContact(contactId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@UiThread
	void sendMessage(@Nullable String text,
			List<AttachmentHeader> headers, long timestamp) {
		// messagingGroupId is loaded with the contact
		observeForeverOnce(messagingGroupId, groupId -> {
			requireNonNull(groupId);
			observeForeverOnce(imageSupport, hasImageSupport -> {
				requireNonNull(hasImageSupport);
				createMessage(groupId, text, headers, timestamp,
						hasImageSupport);
			});
		});
	}

	@Override
	@UiThread
	public LiveData<AttachmentResult> storeAttachments(Collection<Uri> uris,
			boolean restart) {
		if (restart) {
			return attachmentCreator.getLiveAttachments();
		} else {
			// messagingGroupId is loaded with the contact
			return attachmentCreator.storeAttachments(messagingGroupId, uris);
		}
	}

	@Override
	@UiThread
	public List<AttachmentHeader> getAttachmentHeadersForSending() {
		return attachmentCreator.getAttachmentHeadersForSending();
	}

	@Override
	@UiThread
	public void cancel() {
		attachmentCreator.cancel();
	}

	@DatabaseExecutor
	private void checkFeaturesAndOnboarding(ContactId c) throws DbException {
		// check if images are supported
		boolean imagesSupported = db.transactionWithResult(true, txn ->
				messagingManager.contactSupportsImages(txn, c));
		imageSupport.postValue(imagesSupported);

		// check if introductions are supported
		Collection<Contact> contacts = contactManager.getContacts();
		boolean introductionSupported = contacts.size() > 1;
		showIntroductionAction.postValue(introductionSupported);

		Settings settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
		if (imagesSupported &&
				settings.getBoolean(SHOW_ONBOARDING_IMAGE, true)) {
			// check if we should show onboarding, only if images are supported
			showImageOnboarding.postEvent(true);
			// allow observer to stop listening for changes
			showIntroductionOnboarding.postEvent(false);
		} else {
			// allow observer to stop listening for changes
			showImageOnboarding.postEvent(false);
			// we only show one onboarding dialog at a time
			if (introductionSupported &&
					settings.getBoolean(SHOW_ONBOARDING_INTRODUCTION, true)) {
				showIntroductionOnboarding.postEvent(true);
			} else {
				// allow observer to stop listening for changes
				showIntroductionOnboarding.postEvent(false);
			}
		}
	}

	@UiThread
	void onImageOnboardingSeen() {
		onOnboardingSeen(SHOW_ONBOARDING_IMAGE);
	}

	@UiThread
	void onIntroductionOnboardingSeen() {
		onOnboardingSeen(SHOW_ONBOARDING_INTRODUCTION);
	}

	private void onOnboardingSeen(String key) {
		dbExecutor.execute(() -> {
			try {
				Settings settings = new Settings();
				settings.putBoolean(key, false);
				settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void createMessage(GroupId groupId, @Nullable String text,
			List<AttachmentHeader> headers, long timestamp,
			boolean hasImageSupport) {
		try {
			PrivateMessage pm;
			if (hasImageSupport) {
				pm = privateMessageFactory.createPrivateMessage(groupId,
						timestamp, text, headers);
			} else {
				pm = privateMessageFactory.createLegacyPrivateMessage(
						groupId, timestamp, requireNonNull(text));
			}
			storeMessage(pm);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private void storeMessage(PrivateMessage m) {
		attachmentCreator.onAttachmentsSent(m.getMessage().getId());
		dbExecutor.execute(() -> {
			try {
				long start = now();
				messagingManager.addLocalMessage(m);
				logDuration(LOG, "Storing message", start);
				Message message = m.getMessage();
				PrivateMessageHeader h = new PrivateMessageHeader(
						message.getId(), message.getGroupId(),
						message.getTimestamp(), true, true, false, false,
						m.hasText(), m.getAttachmentHeaders());
				// TODO add text to cache when available here
				addedHeader.postEvent(h);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	AttachmentRetriever getAttachmentRetriever() {
		return attachmentRetriever;
	}

	LiveData<Contact> getContact() {
		return contact;
	}

	LiveData<AuthorId> getContactAuthorId() {
		return contactAuthorId;
	}

	LiveData<String> getContactDisplayName() {
		return contactName;
	}

	LiveData<Boolean> hasImageSupport() {
		return imageSupport;
	}

	LiveEvent<Boolean> showImageOnboarding() {
		return showImageOnboarding;
	}

	LiveEvent<Boolean> showIntroductionOnboarding() {
		return showIntroductionOnboarding;
	}

	LiveData<Boolean> showIntroductionAction() {
		return showIntroductionAction;
	}

	LiveData<Boolean> isContactDeleted() {
		return contactDeleted;
	}

	LiveEvent<PrivateMessageHeader> getAddedPrivateMessage() {
		return addedHeader;
	}

}
