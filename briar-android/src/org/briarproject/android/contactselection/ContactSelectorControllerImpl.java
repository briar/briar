package org.briarproject.android.contactselection;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public abstract class ContactSelectorControllerImpl<I extends SelectableContactItem>
		extends DbControllerImpl
		implements ContactSelectorController<I> {

	protected static final Logger LOG =
			Logger.getLogger("ContactSelectorController");

	private final ContactManager contactManager;

	public ContactSelectorControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, ContactManager contactManager) {
		super(dbExecutor, lifecycleManager);
		this.contactManager = contactManager;
	}

	@Override
	public void loadContacts(final GroupId g,
			@Nullable final Collection<ContactId> selection,
			final ResultExceptionHandler<Collection<I>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Collection<I> contacts = new ArrayList<>();
					for (Contact c : contactManager.getActiveContacts()) {
						// was this contact already selected?
						boolean selected = isSelected(c, selection != null &&
								selection.contains(c.getId()));
						// can this contact be selected?
						boolean disabled = isDisabled(g, c);
						contacts.add(getItem(c, selected, disabled));
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
	protected abstract boolean isSelected(Contact c, boolean wasSelected);

	@DatabaseExecutor
	protected abstract boolean isDisabled(GroupId g, Contact c)
			throws DbException;

	protected abstract I getItem(Contact c, boolean selected, boolean disabled);

}
