package org.briarproject.briar.android.contactselection;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import static java.util.logging.Level.WARNING;

@Immutable
@NotNullByDefault
public abstract class ContactSelectorControllerImpl
		extends DbControllerImpl
		implements ContactSelectorController<SelectableContactItem> {

	private static final Logger LOG =
			Logger.getLogger(ContactSelectorControllerImpl.class.getName());

	private final ContactManager contactManager;

	public ContactSelectorControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, ContactManager contactManager) {
		super(dbExecutor, lifecycleManager);
		this.contactManager = contactManager;
	}

	@Override
	public void loadContacts(final GroupId g,
			final Collection<ContactId> selection,
			final ResultExceptionHandler<Collection<SelectableContactItem>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Collection<SelectableContactItem> contacts =
							new ArrayList<>();
					for (Contact c : contactManager.getActiveContacts()) {
						// was this contact already selected?
						boolean selected = selection.contains(c.getId());
						// can this contact be selected?
						boolean disabled = isDisabled(g, c);
						contacts.add(new SelectableContactItem(c, selected,
								disabled));
					}
					handler.onResult(contacts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract boolean isDisabled(GroupId g, Contact c)
			throws DbException;

}
