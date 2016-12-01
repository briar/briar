package org.briarproject.briar.android.privategroup.creation;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.contactselection.ContactSelectorControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

@Immutable
@NotNullByDefault
class CreateGroupControllerImpl extends ContactSelectorControllerImpl
		implements CreateGroupController {

	private static final Logger LOG =
			Logger.getLogger(CreateGroupControllerImpl.class.getName());

	private final Executor cryptoExecutor;
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final PrivateGroupFactory groupFactory;
	private final GroupMessageFactory groupMessageFactory;
	private final PrivateGroupManager groupManager;
	private final GroupInvitationFactory groupInvitationFactory;
	private final GroupInvitationManager groupInvitationManager;
	private final Clock clock;

	@Inject
	CreateGroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			LifecycleManager lifecycleManager, ContactManager contactManager,
			IdentityManager identityManager, PrivateGroupFactory groupFactory,
			GroupMessageFactory groupMessageFactory,
			PrivateGroupManager groupManager,
			GroupInvitationFactory groupInvitationFactory,
			GroupInvitationManager groupInvitationManager, Clock clock) {
		super(dbExecutor, lifecycleManager, contactManager);
		this.cryptoExecutor = cryptoExecutor;
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		this.groupFactory = groupFactory;
		this.groupMessageFactory = groupMessageFactory;
		this.groupManager = groupManager;
		this.groupInvitationFactory = groupInvitationFactory;
		this.groupInvitationManager = groupInvitationManager;
		this.clock = clock;
	}

	@Override
	public void createGroup(final String name,
			final ResultExceptionHandler<GroupId, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor author = identityManager.getLocalAuthor();
					createGroupAndMessages(author, name, handler);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	private void createGroupAndMessages(final LocalAuthor author,
			final String name,
			final ResultExceptionHandler<GroupId, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				LOG.info("Creating group...");
				PrivateGroup group =
						groupFactory.createPrivateGroup(name, author);
				LOG.info("Creating new join announcement...");
				GroupMessage joinMsg = groupMessageFactory
						.createJoinMessage(group.getId(),
								clock.currentTimeMillis(), author);
				storeGroup(group, joinMsg, handler);
			}
		});
	}

	private void storeGroup(final PrivateGroup group,
			final GroupMessage joinMsg,
			final ResultExceptionHandler<GroupId, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Adding group to database...");
				try {
					groupManager.addPrivateGroup(group, joinMsg, true);
					handler.onResult(group.getId());
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	protected boolean isDisabled(GroupId g, Contact c) throws DbException {
		return !groupInvitationManager.isInvitationAllowed(c, g);
	}

	@Override
	public void sendInvitation(final GroupId g,
			final Collection<ContactId> contactIds, final String message,
			final ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					LocalAuthor localAuthor = identityManager.getLocalAuthor();
					List<Contact> contacts = new ArrayList<>();
					for (ContactId c : contactIds) {
						try {
							contacts.add(contactManager.getContact(c));
						} catch (NoSuchContactException e) {
							// Continue
						}
					}
					signInvitations(g, localAuthor, contacts, message, handler);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	private void signInvitations(final GroupId g, final LocalAuthor localAuthor,
			final Collection<Contact> contacts, final String message,
			final ResultExceptionHandler<Void, DbException> handler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				long timestamp = clock.currentTimeMillis();
				List<InvitationContext> contexts = new ArrayList<>();
				for (Contact c : contacts) {
					byte[] signature = groupInvitationFactory.signInvitation(c,
							g, timestamp, localAuthor.getPrivateKey());
					contexts.add(new InvitationContext(c.getId(), timestamp,
							signature));
				}
				sendInvitations(g, contexts, message, handler);
			}
		});
	}

	private void sendInvitations(final GroupId g,
			final Collection<InvitationContext> contexts, final String message,
			final ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					String msg = message.isEmpty() ? null : message;
					for (InvitationContext context : contexts) {
						try {
							groupInvitationManager.sendInvitation(g,
									context.contactId, msg, context.timestamp,
									context.signature);
						} catch (NoSuchContactException e) {
							// Continue
						}
					}
					//noinspection ConstantConditions
					handler.onResult(null);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	private static class InvitationContext {

		private final ContactId contactId;
		private final long timestamp;
		private final byte[] signature;

		private InvitationContext(ContactId contactId, long timestamp,
				byte[] signature) {
			this.contactId = contactId;
			this.timestamp = timestamp;
			this.signature = signature;
		}
	}
}
