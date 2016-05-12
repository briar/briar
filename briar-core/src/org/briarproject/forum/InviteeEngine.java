package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ProtocolEngine;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.forum.ForumConstants.GROUP_ID;
import static org.briarproject.api.forum.ForumConstants.SESSION_ID;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_SHARED_FORUM;
import static org.briarproject.api.forum.ForumConstants.TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_UNSHARE_FORUM_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TYPE;
import static org.briarproject.forum.InviteeSessionState.Action;
import static org.briarproject.forum.InviteeSessionState.Action.LOCAL_ABORT;
import static org.briarproject.forum.InviteeSessionState.Action.LOCAL_ACCEPT;
import static org.briarproject.forum.InviteeSessionState.Action.LOCAL_DECLINE;
import static org.briarproject.forum.InviteeSessionState.Action.LOCAL_LEAVE;
import static org.briarproject.forum.InviteeSessionState.Action.REMOTE_INVITATION;
import static org.briarproject.forum.InviteeSessionState.Action.REMOTE_LEAVE;
import static org.briarproject.forum.InviteeSessionState.State;
import static org.briarproject.forum.InviteeSessionState.State.ERROR;
import static org.briarproject.forum.InviteeSessionState.State.FINISHED;
import static org.briarproject.forum.InviteeSessionState.State.LEFT;

public class InviteeEngine
		implements ProtocolEngine<Action, InviteeSessionState, BdfDictionary> {

	private final ForumFactory forumFactory;
	private static final Logger LOG =
			Logger.getLogger(SharerEngine.class.getName());

	InviteeEngine(ForumFactory forumFactory) {
		this.forumFactory = forumFactory;
	}

	@Override
	public StateUpdate<InviteeSessionState, BdfDictionary> onLocalAction(
			InviteeSessionState localState, Action action) {

		try {
			State currentState = localState.getState();
			State nextState = currentState.next(action);
			localState.setState(nextState);

			if (action == LOCAL_ABORT && currentState != ERROR) {
				return abortSession(currentState, localState);
			}

			if (nextState == ERROR) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Error: Invalid action in state " +
							currentState.name());
				}
				return noUpdate(localState, true);
			}
			List<BdfDictionary> messages;
			List<Event> events = Collections.emptyList();

			if (action == LOCAL_ACCEPT || action == LOCAL_DECLINE) {
				BdfDictionary msg = BdfDictionary.of(
						new BdfEntry(SESSION_ID, localState.getSessionId()),
						new BdfEntry(GROUP_ID, localState.getGroupId())
				);
				if (action == LOCAL_ACCEPT) {
					localState.setTask(TASK_ADD_SHARED_FORUM);
					msg.put(TYPE, SHARE_MSG_TYPE_ACCEPT);
				} else {
					localState.setTask(
							TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US);
					msg.put(TYPE, SHARE_MSG_TYPE_DECLINE);
				}
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else if (action == LOCAL_LEAVE) {
				BdfDictionary msg = new BdfDictionary();
				msg.put(TYPE, SHARE_MSG_TYPE_LEAVE);
				msg.put(SESSION_ID, localState.getSessionId());
				msg.put(GROUP_ID, localState.getGroupId());
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else {
				throw new IllegalArgumentException("Unknown Local Action");
			}
			return new StateUpdate<InviteeSessionState, BdfDictionary>(false,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<InviteeSessionState, BdfDictionary> onMessageReceived(
			InviteeSessionState localState, BdfDictionary msg) {

		try {
			State currentState = localState.getState();
			long type = msg.getLong(TYPE);
			Action action = Action.getRemote(type);
			State nextState = currentState.next(action);
			localState.setState(nextState);

			logMessageReceived(currentState, nextState, type, msg);

			if (nextState == ERROR) {
				if (currentState != ERROR) {
					return abortSession(currentState, localState);
				} else {
					return noUpdate(localState, true);
				}
			}

			List<BdfDictionary> messages = Collections.emptyList();
			List<Event> events = Collections.emptyList();
			boolean deleteMsg = false;

			if (currentState == LEFT) {
				// ignore and delete messages coming in while in that state
				deleteMsg = true;
			}
			// the sharer left the forum she had shared with us
			else if (action == REMOTE_LEAVE && currentState == FINISHED) {
				localState.setTask(TASK_UNSHARE_FORUM_SHARED_WITH_US);
			}
			else if (currentState == FINISHED) {
				// ignore and delete messages coming in while in that state
				// note that LEAVE is possible, but was handled above
				deleteMsg = true;
			}
			// the sharer left the forum before we couldn't even respond
			else if (action == REMOTE_LEAVE) {
				localState.setTask(TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US);
			}
			// we have just received our invitation
			else if (action == REMOTE_INVITATION) {
				Forum forum = forumFactory
						.createForum(localState.getForumName(),
								localState.getForumSalt());
				localState.setTask(TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US);
				ContactId contactId = localState.getContactId();
				Event event = new ForumInvitationReceivedEvent(forum, contactId);
				events = Collections.singletonList(event);
			}
			else {
				throw new IllegalArgumentException("Bad state");
			}
			return new StateUpdate<InviteeSessionState, BdfDictionary>(deleteMsg,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void logLocalAction(State state,
			InviteeSessionState localState, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		String a = "response";
		if (msg.getLong(TYPE, -1L) == SHARE_MSG_TYPE_LEAVE) a = "leave";

		try {
			LOG.info("Sending " + a + " in state " + state.name() +
					" with session ID " +
					Arrays.hashCode(msg.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(msg.getRaw(GROUP_ID)) + ". " +
					"Moving on to state " + localState.getState().name()
			);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void logMessageReceived(State currentState, State nextState,
			long type, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		try {
			String t = "unknown";
			if (type == SHARE_MSG_TYPE_INVITATION) t = "INVITE";
			else if (type == SHARE_MSG_TYPE_LEAVE) t = "LEAVE";
			else if (type == SHARE_MSG_TYPE_ABORT) t = "ABORT";

			LOG.info("Received " + t + " in state " + currentState.name() +
					" with session ID " +
					Arrays.hashCode(msg.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(msg.getRaw(GROUP_ID)) + ". " +
					"Moving on to state " + nextState.name()
			);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public StateUpdate<InviteeSessionState, BdfDictionary> onMessageDelivered(
			InviteeSessionState localState, BdfDictionary delivered) {
		try {
			return noUpdate(localState, false);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private StateUpdate<InviteeSessionState, BdfDictionary> abortSession(
			State currentState, InviteeSessionState localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					localState.getSessionId().hashCode() +
					" in state " + currentState.name());
		}

		localState.setState(ERROR);
		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, SHARE_MSG_TYPE_ABORT);
		msg.put(SESSION_ID, localState.getSessionId());
		msg.put(GROUP_ID, localState.getGroupId());
		List<BdfDictionary> messages = Collections.singletonList(msg);

		List<Event> events = Collections.emptyList();

		return new StateUpdate<InviteeSessionState, BdfDictionary>(false, false,
				localState, messages, events);
	}

	private StateUpdate<InviteeSessionState, BdfDictionary> noUpdate(
			InviteeSessionState localState, boolean delete) throws FormatException {

		return new StateUpdate<InviteeSessionState, BdfDictionary>(delete, false,
				localState, Collections.<BdfDictionary>emptyList(),
				Collections.<Event>emptyList());
	}
}
