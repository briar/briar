package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ProtocolEngine;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.ForumInvitationResponseReceivedEvent;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_REMOVE_FORUM_FROM_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_SHARE_FORUM;
import static org.briarproject.api.forum.ForumConstants.TASK_UNSHARE_FORUM_SHARED_BY_US;
import static org.briarproject.api.forum.ForumSharingMessage.BaseMessage;
import static org.briarproject.api.forum.ForumSharingMessage.Invitation;
import static org.briarproject.api.forum.ForumSharingMessage.SimpleMessage;
import static org.briarproject.forum.SharerSessionState.Action;
import static org.briarproject.forum.SharerSessionState.Action.LOCAL_ABORT;
import static org.briarproject.forum.SharerSessionState.Action.LOCAL_INVITATION;
import static org.briarproject.forum.SharerSessionState.Action.LOCAL_LEAVE;
import static org.briarproject.forum.SharerSessionState.Action.REMOTE_ACCEPT;
import static org.briarproject.forum.SharerSessionState.Action.REMOTE_DECLINE;
import static org.briarproject.forum.SharerSessionState.Action.REMOTE_LEAVE;
import static org.briarproject.forum.SharerSessionState.State;
import static org.briarproject.forum.SharerSessionState.State.ERROR;
import static org.briarproject.forum.SharerSessionState.State.FINISHED;
import static org.briarproject.forum.SharerSessionState.State.LEFT;

public class SharerEngine
		implements ProtocolEngine<Action, SharerSessionState, BaseMessage> {

	private static final Logger LOG =
			Logger.getLogger(SharerEngine.class.getName());

	@Override
	public StateUpdate<SharerSessionState, BaseMessage> onLocalAction(
			SharerSessionState localState, Action action) {

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

			if (action == LOCAL_INVITATION) {
				BaseMessage msg = new Invitation(localState.getGroupId(),
						localState.getSessionId(), localState.getForumName(),
						localState.getForumSalt(), localState.getMessage());
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, nextState, msg);

				// remember that we offered to share this forum
				localState.setTask(TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US);
			}
			else if (action == LOCAL_LEAVE) {
				BaseMessage msg = new SimpleMessage(SHARE_MSG_TYPE_LEAVE,
						localState.getGroupId(), localState.getSessionId());
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, nextState, msg);
			}
			else {
				throw new IllegalArgumentException("Unknown Local Action");
			}
			return new StateUpdate<SharerSessionState, BaseMessage>(false,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<SharerSessionState, BaseMessage> onMessageReceived(
			SharerSessionState localState, BaseMessage msg) {

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
			else if (action == REMOTE_LEAVE) {
				localState.setTask(TASK_UNSHARE_FORUM_SHARED_BY_US);
			}
			else if (currentState == FINISHED) {
				// ignore and delete messages coming in while in that state
				// note that LEAVE is possible, but was handled above
				deleteMsg = true;
			}
			// we have sent our invitation and just got a response
			else if (action == REMOTE_ACCEPT || action == REMOTE_DECLINE) {
				if (action == REMOTE_ACCEPT) {
					localState.setTask(TASK_SHARE_FORUM);
				} else {
					// this ensures that the forum can be shared again
					localState.setTask(
							TASK_REMOVE_FORUM_FROM_LIST_TO_BE_SHARED_BY_US);
				}
				String name = localState.getForumName();
				ContactId c = localState.getContactId();
				Event event = new ForumInvitationResponseReceivedEvent(name, c);
				events = Collections.singletonList(event);
			}
			else {
				throw new IllegalArgumentException("Bad state");
			}
			return new StateUpdate<SharerSessionState, BaseMessage>(deleteMsg,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void logLocalAction(State currentState, State nextState,
			BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String a = "invitation";
		if (msg.getType() == SHARE_MSG_TYPE_LEAVE) a = "leave";

		LOG.info("Sending " + a + " in state " + currentState.name() +
				" with session ID " +
				msg.getSessionId().hashCode() + " in group " +
				msg.getGroupId().hashCode() + ". " +
				"Moving on to state " + nextState.name()
		);
	}

	private void logMessageReceived(State currentState, State nextState,
			long type, BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String t = "unknown";
		if (type == SHARE_MSG_TYPE_ACCEPT) t = "ACCEPT";
		else if (type == SHARE_MSG_TYPE_DECLINE) t = "DECLINE";
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
	public StateUpdate<SharerSessionState, BaseMessage> onMessageDelivered(
			SharerSessionState localState, BaseMessage delivered) {
		try {
			return noUpdate(localState, false);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private StateUpdate<SharerSessionState, BaseMessage> abortSession(
			State currentState, SharerSessionState localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					localState.getSessionId().hashCode() +
					" in state " + currentState.name());
		}

		localState.setState(ERROR);
		BaseMessage msg = new SimpleMessage(SHARE_MSG_TYPE_ABORT,
				localState.getGroupId(), localState.getSessionId());
		List<BaseMessage> messages = Collections.singletonList(msg);

		List<Event> events = Collections.emptyList();

		return new StateUpdate<SharerSessionState, BaseMessage>(false, false,
				localState, messages, events);
	}

	private StateUpdate<SharerSessionState, BaseMessage> noUpdate(
			SharerSessionState localState, boolean delete)
			throws FormatException {

		return new StateUpdate<SharerSessionState, BaseMessage>(delete, false,
				localState, Collections.<BaseMessage>emptyList(),
				Collections.<Event>emptyList());
	}

}
