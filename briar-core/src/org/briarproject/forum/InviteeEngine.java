package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ProtocolEngine;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumFactory;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_SHARED_FORUM;
import static org.briarproject.api.forum.ForumConstants.TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_UNSHARE_FORUM_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumSharingMessage.SimpleMessage;
import static org.briarproject.api.forum.ForumSharingMessage.BaseMessage;
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
		implements ProtocolEngine<Action, InviteeSessionState, BaseMessage> {

	private final ForumFactory forumFactory;
	private static final Logger LOG =
			Logger.getLogger(SharerEngine.class.getName());

	InviteeEngine(ForumFactory forumFactory) {
		this.forumFactory = forumFactory;
	}

	@Override
	public StateUpdate<InviteeSessionState, BaseMessage> onLocalAction(
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
			List<BaseMessage> messages;
			List<Event> events = Collections.emptyList();

			if (action == LOCAL_ACCEPT || action == LOCAL_DECLINE) {
				BaseMessage msg;
				if (action == LOCAL_ACCEPT) {
					localState.setTask(TASK_ADD_SHARED_FORUM);
					msg = new SimpleMessage(SHARE_MSG_TYPE_ACCEPT,
							localState.getGroupId(), localState.getSessionId());
				} else {
					localState.setTask(
							TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US);
					msg = new SimpleMessage(SHARE_MSG_TYPE_DECLINE,
							localState.getGroupId(), localState.getSessionId());
				}
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else if (action == LOCAL_LEAVE) {
				BaseMessage msg = new SimpleMessage(SHARE_MSG_TYPE_LEAVE,
						localState.getGroupId(), localState.getSessionId());
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else {
				throw new IllegalArgumentException("Unknown Local Action");
			}
			return new StateUpdate<InviteeSessionState, BaseMessage>(false,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<InviteeSessionState, BaseMessage> onMessageReceived(
			InviteeSessionState localState, BaseMessage msg) {

		try {
			State currentState = localState.getState();
			Action action = Action.getRemote(msg.getType());
			State nextState = currentState.next(action);
			localState.setState(nextState);

			logMessageReceived(currentState, nextState, msg.getType(), msg);

			if (nextState == ERROR) {
				if (currentState != ERROR) {
					return abortSession(currentState, localState);
				} else {
					return noUpdate(localState, true);
				}
			}

			List<BaseMessage> messages = Collections.emptyList();
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
			return new StateUpdate<InviteeSessionState, BaseMessage>(deleteMsg,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void logLocalAction(State state,
			InviteeSessionState localState, BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String a = "response";
		if (msg.getType() == SHARE_MSG_TYPE_LEAVE) a = "leave";

		LOG.info("Sending " + a + " in state " + state.name() +
				" with session ID " +
				msg.getSessionId().hashCode() + " in group " +
				msg.getGroupId().hashCode() + ". " +
				"Moving on to state " + localState.getState().name()
		);
	}

	private void logMessageReceived(State currentState, State nextState,
			long type, BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String t = "unknown";
		if (type == SHARE_MSG_TYPE_INVITATION) t = "INVITE";
		else if (type == SHARE_MSG_TYPE_LEAVE) t = "LEAVE";
		else if (type == SHARE_MSG_TYPE_ABORT) t = "ABORT";

		LOG.info("Received " + t + " in state " + currentState.name() +
				" with session ID " +
				msg.getSessionId().hashCode() + " in group " +
				msg.getGroupId().hashCode() + ". " +
				"Moving on to state " + nextState.name()
		);
	}

	@Override
	public StateUpdate<InviteeSessionState, BaseMessage> onMessageDelivered(
			InviteeSessionState localState, BaseMessage delivered) {
		try {
			return noUpdate(localState, false);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private StateUpdate<InviteeSessionState, BaseMessage> abortSession(
			State currentState, InviteeSessionState localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					localState.getSessionId().hashCode() +
					" in state " + currentState.name());
		}
		localState.setState(ERROR);
		BaseMessage msg =
				new SimpleMessage(SHARE_MSG_TYPE_ABORT, localState.getGroupId(),
						localState.getSessionId());
		List<BaseMessage> messages = Collections.singletonList(msg);

		List<Event> events = Collections.emptyList();

		return new StateUpdate<InviteeSessionState, BaseMessage>(false, false,
				localState, messages, events);
	}

	private StateUpdate<InviteeSessionState, BaseMessage> noUpdate(
			InviteeSessionState localState, boolean delete) throws FormatException {

		return new StateUpdate<InviteeSessionState, BaseMessage>(delete, false,
				localState, Collections.<BaseMessage>emptyList(),
				Collections.<Event>emptyList());
	}
}
