package org.briarproject.briar.android.introduction;

import android.app.Application;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.contact.ContactsViewModel;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.identity.AuthorManager;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import androidx.annotation.Nullable;

import static java.util.Objects.requireNonNull;

@NotNullByDefault
class ContactListViewModel extends ContactsViewModel {

	@Nullable
	private ContactId contactId;

	@Inject
	ContactListViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, TransactionManager db,
			AndroidExecutor androidExecutor, ContactManager contactManager,
			AuthorManager authorManager,
			ConversationManager conversationManager,
			ConnectionRegistry connectionRegistry, EventBus eventBus) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor,
				contactManager, authorManager, conversationManager,
				connectionRegistry, eventBus);
	}

	void setContactId(ContactId contactId) {
		this.contactId = contactId;
	}

	@Override
	protected boolean displayContact(ContactId contactId) {
		return !requireNonNull(this.contactId).equals(contactId);
	}

}
