package org.briarproject.android.privategroup.reveal;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ExceptionHandler;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.ProtocolStateException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMember;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.privategroup.Visibility.INVISIBLE;

@Immutable
@NotNullByDefault
public class RevealContactsControllerImpl extends DbControllerImpl
		implements RevealContactsController {

	private static final Logger LOG =
			Logger.getLogger(RevealContactsControllerImpl.class.getName());

	private final PrivateGroupManager groupManager;
	private final GroupInvitationManager groupInvitationManager;
	private final ContactManager contactManager;

	@Inject
	public RevealContactsControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, PrivateGroupManager groupManager,
			GroupInvitationManager groupInvitationManager,
			ContactManager contactManager) {
		super(dbExecutor, lifecycleManager);
		this.groupManager = groupManager;
		this.groupInvitationManager = groupInvitationManager;
		this.contactManager = contactManager;
	}

	@Override
	public void loadContacts(final GroupId g,
			final Collection<ContactId> selection,
			final ResultExceptionHandler<Collection<RevealableContactItem>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Collection<RevealableContactItem> items =
							getItems(g, selection);
					handler.onResult(items);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	private Collection<RevealableContactItem> getItems(GroupId g,
			Collection<ContactId> selection) throws DbException {
		Collection<GroupMember> members =
				groupManager.getMembers(g);
		Collection<Contact> contacts =
				contactManager.getActiveContacts();
		Collection<RevealableContactItem> items =
				new ArrayList<>(members.size());
		for (GroupMember m : members) {
			for (Contact c : contacts) {
				if (m.getAuthor().equals(c.getAuthor())) {
					boolean disabled = m.getVisibility() != INVISIBLE;
					boolean selected =
							disabled || selection.contains(c.getId());
					items.add(new RevealableContactItem(c, selected, disabled,
							m.getVisibility()));
				}

			}
		}
		return items;
	}

	@Override
	public void reveal(final GroupId g, final Collection<ContactId> contacts,
			final ExceptionHandler<DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				for (ContactId c : contacts) {
					try {
						groupInvitationManager.revealRelationship(c, g);
					} catch (ProtocolStateException e) {
						// action is outdated, move to next contact
						if (LOG.isLoggable(INFO))
							LOG.log(INFO, e.toString(), e);
					} catch (DbException e) {
						if (LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
						handler.onException(e);
						break;
					}
				}
			}
		});
	}

}
