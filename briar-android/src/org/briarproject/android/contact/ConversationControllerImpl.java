package org.briarproject.android.contact;

import android.app.Activity;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.FormatException;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.conversation.ConversationManager;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.ConversationItemReceivedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessagesAckedEvent;
import org.briarproject.api.event.MessagesSentEvent;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class ConversationControllerImpl extends DbControllerImpl
		implements ConversationController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ConversationControllerImpl.class.getName());

	@Inject
	protected Activity activity;
	@Inject
	protected ConnectionRegistry connectionRegistry;
	@Inject
	@CryptoExecutor
	protected Executor cryptoExecutor;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ContactManager contactManager;
	@Inject
	protected volatile ConversationManager conversationManager;
	@Inject
	protected volatile EventBus eventBus;
	@Inject
	protected volatile PrivateMessageFactory privateMessageFactory;
	@Inject
	protected ConversationPersistentData data;

	private ConversationListener listener;

	@Inject
	ConversationControllerImpl() {
	}

	@Override
	public void onActivityCreate() {
		if (activity instanceof ConversationListener) {
			listener = (ConversationListener) activity;
		} else {
			throw new IllegalStateException(
					"An activity that injects the ConversationController " +
							"must implement the ConversationListener");
		}
	}

	@Override
	public void onActivityResume() {
		eventBus.addListener(this);
	}

	@Override
	public void onActivityPause() {
		eventBus.removeListener(this);
	}

	@Override
	public void onActivityDestroy() {
		if (activity.isFinishing()) {
			data.clearAll();
		}
	}

	@Override
	public void loadConversation(final GroupId groupId,
			final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (data.getGroupId() == null ||
							!data.getGroupId().equals(groupId)) {
						data.setGroupId(groupId);
						long now = System.currentTimeMillis();
						ContactId contactId =
								conversationManager.getContactId(groupId);
						data.setContact(contactManager.getContact(contactId));
						data.setConnected(
								connectionRegistry.isConnected(contactId));
						long duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info(
									"Loading contact took " + duration + " ms");
					}
					resultHandler.onResult(true);
				} catch (NoSuchContactException e) {
					resultHandler.onResult(false);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

	@Override
	public void loadMessages(final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (getContactId() != null) {
						long now = System.currentTimeMillis();
						data.addConversationItems(
								conversationManager
										.getMessages(getContactId()));
						long duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info(
									"Loading headers took " + duration + " ms");
					}
					resultHandler.onResult(true);
				} catch (NoSuchContactException e) {
					resultHandler.onResult(false);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

	@Override
	public void createMessage(final byte[] body, final long timestamp,
			final UiResultHandler<ConversationItem> resultHandler) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					PrivateMessage m = privateMessageFactory
							.createPrivateMessage(data.getGroupId(), timestamp,
									null, "text/plain", body);
					storeMessage(m, body, resultHandler);
				} catch (FormatException e) {
					// TODO why was this being thrown?
					//throw new RuntimeException(e);
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(null);
				}
			}
		});
	}

	private void storeMessage(final PrivateMessage m, final byte[] body,
			final UiResultHandler<ConversationItem> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					ConversationItem item =
							conversationManager.addLocalMessage(m, body);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
					resultHandler.onResult(item);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(null);
				}
			}
		});
	}

	@Override
	public ContactId getContactId() {
		return data.getContact() == null ? null : data.getContact().getId();
	}

	@Override
	public String getContactName() {
		return data.getContact() == null ? null :
				data.getContact().getAuthor().getName();
	}

	@Override
	public byte[] getContactIdenticonKey() {
		return data.getContact() == null ? null :
				data.getContact().getAuthor().getId().getBytes();
	}

	@Override
	public List<ConversationItem> getConversationItems() {
		return data.getConversationItems();
	}

	@Override
	public boolean isConnected() {
		return data.isConnected();
	}

	@Override
	public void markMessagesRead(final Collection<ConversationItem> unread,
			final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (getContactId() != null) {
						long now = System.currentTimeMillis();
						for (ConversationItem item : unread)
							conversationManager
									.setReadFlag(getContactId(), item, true);
						long duration = System.currentTimeMillis() - now;
						if (LOG.isLoggable(INFO))
							LOG.info("Marking read took " + duration + " ms");
					}
					resultHandler.onResult(true);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

	@Override
	public void markNewMessageRead(final ConversationItem item) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (getContactId() != null) {
						conversationManager
								.setReadFlag(getContactId(), item, true);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void removeContact(final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					if (getContactId() != null) {
						contactManager.removeContact(getContactId());
					}
					resultHandler.onResult(true);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

	@Override
	public void respondToItem(final ConversationItem item, final boolean accept,
			final long minTimestamp,
			final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				long timestamp = System.currentTimeMillis();
				timestamp = Math.max(timestamp, minTimestamp);
				try {
					conversationManager
							.respondToItem(getContactId(), item, accept,
									timestamp);
					resultHandler.onResult(true);
				} catch (DbException | FormatException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

	@Override
	public void shouldHideIntroductionAction(
			final UiResultHandler<Boolean> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					resultHandler.onResult(
							contactManager.getActiveContacts().size() < 2);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					resultHandler.onResult(false);
				}
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(getContactId())) {
				LOG.info("Contact removed");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						activity.finish();
					}
				});
			}
		} else if (e instanceof ConversationItemReceivedEvent) {
			final ConversationItemReceivedEvent event =
					(ConversationItemReceivedEvent) e;
			if (event.getContactId().equals(getContactId())) {
				LOG.info("Message received, adding");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						listener.messageReceived(event.getItem());
					}
				});
			}
		} else if (e instanceof MessagesSentEvent) {
			final MessagesSentEvent m = (MessagesSentEvent) e;
			if (m.getContactId().equals(getContactId())) {
				LOG.info("Messages sent");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						listener.markMessages(m.getMessageIds(), true, false);
					}
				});
			}
		} else if (e instanceof MessagesAckedEvent) {
			final MessagesAckedEvent m = (MessagesAckedEvent) e;
			if (m.getContactId().equals(getContactId())) {
				LOG.info("Messages acked");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						listener.markMessages(m.getMessageIds(), true, true);
					}
				});
			}
		} else if (e instanceof ContactConnectedEvent) {
			ContactConnectedEvent c = (ContactConnectedEvent) e;
			if (c.getContactId().equals(getContactId())) {
				LOG.info("Contact connected");
				data.setConnected(true);
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						listener.contactUpdated();
					}
				});
			}
		} else if (e instanceof ContactDisconnectedEvent) {
			ContactDisconnectedEvent c = (ContactDisconnectedEvent) e;
			if (c.getContactId().equals(getContactId())) {
				LOG.info("Contact disconnected");
				data.setConnected(false);
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						listener.contactUpdated();
					}
				});
			}
		}
	}
}
