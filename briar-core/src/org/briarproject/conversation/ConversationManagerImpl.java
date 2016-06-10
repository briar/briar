package org.briarproject.conversation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.conversation.ConversationForumInvitationItem;
import org.briarproject.api.conversation.ConversationIntroductionRequestItem;
import org.briarproject.api.conversation.ConversationIntroductionResponseItem;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.conversation.ConversationItem.OutgoingItem;
import org.briarproject.api.conversation.ConversationItem.Partial;
import org.briarproject.api.conversation.ConversationManager;
import org.briarproject.api.conversation.ConversationMessageItem;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.event.ConversationItemReceivedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.event.IntroductionResponseReceivedEvent;
import org.briarproject.api.event.PrivateMessageReceivedEvent;
import org.briarproject.api.forum.ForumInvitationMessage;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.introduction.IntroductionMessage;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class ConversationManagerImpl implements ConversationManager,
		EventListener {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"05313ec596871e5305181220488fc9c0"
					+ "3d44985a48a6d0fb8767da3a6cae1cb4"));

	private static final Logger LOG =
			Logger.getLogger(ConversationManagerImpl.class.getName());

	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ForumSharingManager forumSharingManager;
	private final IntroductionManager introductionManager;
	private final MessagingManager messagingManager;

	private Map<MessageId, byte[]> bodyCache =
			new ConcurrentHashMap<MessageId, byte[]>();

	@Inject
	ConversationManagerImpl(@DatabaseExecutor Executor dbExecutor,
			EventBus eventBus, ForumSharingManager forumSharingManager,
			IntroductionManager introductionManager,
			MessagingManager messagingManager) {
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.forumSharingManager = forumSharingManager;
		this.introductionManager = introductionManager;
		this.messagingManager = messagingManager;

		eventBus.addListener(this);
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public ConversationItem addLocalMessage(PrivateMessage m, byte[] body)
			throws DbException {
		messagingManager.addLocalMessage(m);
		bodyCache.put(m.getMessage().getId(), body);

		PrivateMessageHeader h = new PrivateMessageHeader(
				m.getMessage().getId(),
				m.getMessage().getTimestamp(), m.getContentType(),
				true, false, false, false);
		ConversationItem item = ConversationMessageItemImpl.from(h);
		((Partial) item).setContent(body);
		return item;
	}

	@Override
	public ContactId getContactId(GroupId g) throws DbException {
		return messagingManager.getContactId(g);
	}

	@Override
	public GroupId getConversationId(ContactId c) throws DbException {
		return messagingManager.getConversationId(c);
	}

	@Override
	public List<ConversationItem> getMessages(ContactId c)
			throws DbException {
		Collection<PrivateMessageHeader> headers =
				messagingManager.getMessageHeaders(c);
		Collection<IntroductionMessage> introductions =
				introductionManager.getIntroductionMessages(c);
		Collection<ForumInvitationMessage> invitations =
				forumSharingManager.getInvitationMessages(c);
		List<ConversationItem> items = new ArrayList<ConversationItem>();
		for (PrivateMessageHeader h : headers) {
			ConversationItem item = ConversationMessageItemImpl.from(h);
			byte[] body = bodyCache.get(h.getId());
			if (body == null) loadMessageContent((Partial) item);
			else ((Partial) item).setContent(body);
			items.add(item);
		}
		for (IntroductionMessage m : introductions) {
			ConversationItem item;
			if (m instanceof IntroductionRequest) {
				item = ConversationIntroductionRequestItemImpl.from(
						(IntroductionRequest) m);
			} else {
				item = ConversationIntroductionResponseItemImpl.from(
						(IntroductionResponse) m);
			}
			items.add(item);
		}
		for (ForumInvitationMessage i : invitations) {
			ConversationItem item = ConversationForumInvitationItemImpl.from(i);
			items.add(item);
		}
		return items;
	}

	@Override
	public long getTimestamp(ContactId c) throws DbException {
		long timestamp = -1;
		long t = messagingManager.getTimestamp(c);
		if (t > timestamp) timestamp = t;
		t = introductionManager.getTimestamp(c);
		if (t > timestamp) timestamp = t;
		t = forumSharingManager.getTimestamp(c);
		if (t > timestamp) timestamp = t;
		return timestamp;
	}

	@Override
	public int getUnreadCount(ContactId c) throws DbException {
		int unread = 0;
		unread += messagingManager.getUnreadCount(c);
		unread += introductionManager.getUnreadCount(c);
		unread += forumSharingManager.getUnreadCount(c);
		return unread;
	}

	@Override
	public void loadMessageContent(final Partial m) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					MessageId id = m.getId();
					long now = System.currentTimeMillis();
					byte[] body = messagingManager.getMessageBody(id);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading message took " + duration + " ms");
					bodyCache.put(id, body);
					m.setContent(body);
				} catch (NoSuchMessageException e) {
					// The item will be removed when we get the event
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void respondToItem(ContactId c, ConversationItem item,
			boolean accept, long timestamp)
			throws DbException, FormatException {
		if (item instanceof ConversationIntroductionRequestItem) {
			SessionId sessionId = ((ConversationIntroductionRequestItem) item)
					.getIntroductionRequest().getSessionId();
			if (accept) {
				introductionManager
						.acceptIntroduction(c, sessionId,
								timestamp);
			} else {
				introductionManager
						.declineIntroduction(c, sessionId,
								timestamp);
			}
		}
	}

	@Override
	public void setReadFlag(ContactId c, ConversationItem item, boolean read)
			throws DbException {
		MessageId id = item.getId();
		boolean local = item instanceof OutgoingItem;
		if (item instanceof ConversationMessageItem) {
			messagingManager.setReadFlag(c, id, local, read);
		} else if (item instanceof ConversationIntroductionRequestItem ||
				item instanceof ConversationIntroductionResponseItem) {
			introductionManager.setReadFlag(c, id, local, read);
		} else if (item instanceof ConversationForumInvitationItem) {
			forumSharingManager.setReadFlag(c, id, local, read);
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof PrivateMessageReceivedEvent) {
			PrivateMessageReceivedEvent p = (PrivateMessageReceivedEvent) e;
			PrivateMessageHeader h = p.getMessageHeader();
			ConversationItem m = ConversationMessageItemImpl.from(h);
			loadMessageContent((Partial) m);
			try {
				ContactId c = getContactId(p.getGroupId());
				eventBus.broadcast(new ConversationItemReceivedEvent(m, c));
			} catch (DbException dbe) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, dbe.toString(), dbe);
			}
		} else if (e instanceof IntroductionRequestReceivedEvent) {
			IntroductionRequestReceivedEvent event =
					(IntroductionRequestReceivedEvent) e;
			IntroductionRequest ir = event.getIntroductionRequest();
			ConversationItem item =
					ConversationIntroductionRequestItemImpl.from(ir);
			eventBus.broadcast(
					new ConversationItemReceivedEvent(item,
							event.getContactId()));
		} else if (e instanceof IntroductionResponseReceivedEvent) {
			IntroductionResponseReceivedEvent event =
					(IntroductionResponseReceivedEvent) e;
			IntroductionResponse ir = event.getIntroductionResponse();
			ConversationItem item =
					ConversationIntroductionResponseItemImpl.from(ir);
			eventBus.broadcast(new ConversationItemReceivedEvent(item,
					event.getContactId()));
		} else if (e instanceof ForumInvitationReceivedEvent) {
			ForumInvitationReceivedEvent event =
					(ForumInvitationReceivedEvent) e;
			try {
				Collection<ForumInvitationMessage> msgs = forumSharingManager
						.getInvitationMessages(event.getContactId());
				for (ForumInvitationMessage i : msgs) {
					if (i.getForumName().equals(event.getForum().getName())) {
						ConversationItem item =
								ConversationForumInvitationItemImpl.from(i);
						eventBus.broadcast(
								new ConversationItemReceivedEvent(item,
										event.getContactId()));
					}
				}
			} catch (DbException dbe) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, dbe.toString(), dbe);
			}
		}
	}
}
