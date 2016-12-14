package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.ProtocolEngine;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.IntroduceeAction;
import org.briarproject.briar.api.introduction.IntroduceeProtocolState;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.api.introduction.IntroduceeAction.ACK;
import static org.briarproject.briar.api.introduction.IntroduceeAction.LOCAL_ABORT;
import static org.briarproject.briar.api.introduction.IntroduceeAction.LOCAL_ACCEPT;
import static org.briarproject.briar.api.introduction.IntroduceeAction.LOCAL_DECLINE;
import static org.briarproject.briar.api.introduction.IntroduceeAction.REMOTE_ABORT;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.AWAIT_ACK;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.AWAIT_REMOTE_RESPONSE;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.AWAIT_RESPONSES;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.ERROR;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.FINISHED;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.briar.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK_ACTIVATE_CONTACT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TASK_ADD_CONTACT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;

@Immutable
@NotNullByDefault
class IntroduceeEngine
		implements ProtocolEngine<BdfDictionary, BdfDictionary, BdfDictionary> {

	private static final Logger LOG =
			Logger.getLogger(IntroduceeEngine.class.getName());

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onLocalAction(
			BdfDictionary localState, BdfDictionary localAction) {

		try {
			IntroduceeProtocolState currentState =
					getState(localState.getLong(STATE));
			int type = localAction.getLong(TYPE).intValue();
			IntroduceeAction action;
			if (localState.containsKey(ACCEPT)) action = IntroduceeAction
					.getLocal(type, localState.getBoolean(ACCEPT));
			else action = IntroduceeAction.getLocal(type);
			IntroduceeProtocolState nextState = currentState.next(action);

			if (action == LOCAL_ABORT && currentState != ERROR) {
				return abortSession(currentState, localState);
			}

			if (nextState == ERROR) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Error: Invalid action in state " +
							currentState.name());
				}
				if (currentState == ERROR) return noUpdate(localState);
				else return abortSession(currentState, localState);
			}

			List<BdfDictionary> messages = new ArrayList<BdfDictionary>(1);
			if (action == LOCAL_ACCEPT || action == LOCAL_DECLINE) {
				localState.put(STATE, nextState.getValue());
				localState.put(ANSWERED, true);
				// create the introduction response message
				BdfDictionary msg = new BdfDictionary();
				msg.put(TYPE, TYPE_RESPONSE);
				msg.put(GROUP_ID, localState.getRaw(GROUP_ID));
				msg.put(SESSION_ID, localState.getRaw(SESSION_ID));
				msg.put(ACCEPT, localState.getBoolean(ACCEPT));
				if (localState.getBoolean(ACCEPT)) {
					msg.put(TIME, localState.getLong(OUR_TIME));
					msg.put(E_PUBLIC_KEY, localState.getRaw(OUR_PUBLIC_KEY));
					msg.put(TRANSPORT, localAction.getDictionary(TRANSPORT));
				}
				msg.put(MESSAGE_TIME, localAction.getLong(MESSAGE_TIME));
				messages.add(msg);
				logAction(currentState, localState, msg);

				if (nextState == AWAIT_ACK) {
					localState.put(TASK, TASK_ADD_CONTACT);
				}
			} else if (action == ACK) {
				// just send ACK, don't update local state again
				BdfDictionary ack = getAckMessage(localState);
				messages.add(ack);
			} else {
				throw new IllegalArgumentException();
			}
			List<Event> events = Collections.emptyList();
			return new StateUpdate<BdfDictionary, BdfDictionary>(false,
					false,
					localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onMessageReceived(
			BdfDictionary localState, BdfDictionary msg) {

		try {
			IntroduceeProtocolState currentState =
					getState(localState.getLong(STATE));
			int type = msg.getLong(TYPE).intValue();
			IntroduceeAction action = IntroduceeAction.getRemote(type);
			IntroduceeProtocolState nextState = currentState.next(action);

			logMessageReceived(currentState, nextState, localState, type, msg);

			if (nextState == ERROR) {
				if (currentState != ERROR && action != REMOTE_ABORT) {
					return abortSession(currentState, localState);
				} else {
					return noUpdate(localState);
				}
			}

			// update local session state with next protocol state
			localState.put(STATE, nextState.getValue());
			List<BdfDictionary> messages;
			List<Event> events;
			// we received the introduction request
			if (currentState == AWAIT_REQUEST) {
				// remember the session ID used by the introducer
				localState.put(SESSION_ID, msg.getRaw(SESSION_ID));

				addRequestData(localState, msg);
				messages = Collections.emptyList();
				events = Collections.singletonList(getEvent(localState, msg));
			}
			// we had the request and now one response came in _OR_
			// we had sent our response already and now received the other one
			else if (currentState == AWAIT_RESPONSES ||
					currentState == AWAIT_REMOTE_RESPONSE) {
				// update next state based on message content
				action = IntroduceeAction
						.getRemote(type, msg.getBoolean(ACCEPT));
				nextState = currentState.next(action);
				localState.put(STATE, nextState.getValue());

				addResponseData(localState, msg);
				if (nextState == AWAIT_ACK) {
					localState.put(TASK, TASK_ADD_CONTACT);
				}
				messages = Collections.emptyList();
				events = Collections.emptyList();
			}
			// we already sent our ACK and now received the other one
			else if (currentState == AWAIT_ACK) {
				localState.put(TASK, TASK_ACTIVATE_CONTACT);
				addAckData(localState, msg);
				messages = Collections.emptyList();
				events = Collections.emptyList();
			}
			// we are done (probably declined response), ignore & delete message
			else if (currentState == FINISHED) {
				return new StateUpdate<BdfDictionary, BdfDictionary>(true,
						false, localState,
						Collections.<BdfDictionary>emptyList(),
						Collections.<Event>emptyList());
			}
			// this should not happen
			else {
				throw new IllegalArgumentException();
			}
			return new StateUpdate<BdfDictionary, BdfDictionary>(false, false,
					localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void addRequestData(BdfDictionary localState, BdfDictionary msg)
			throws FormatException {

		localState.put(NAME, msg.getString(NAME));
		localState.put(PUBLIC_KEY, msg.getRaw(PUBLIC_KEY));
		if (msg.containsKey(MSG)) {
			localState.put(MSG, msg.getString(MSG));
		}
	}

	private void addResponseData(BdfDictionary localState, BdfDictionary msg)
			throws FormatException {

		if (localState.containsKey(ACCEPT)) {
			localState.put(ACCEPT,
					localState.getBoolean(ACCEPT) && msg.getBoolean(ACCEPT));
		} else {
			localState.put(ACCEPT, msg.getBoolean(ACCEPT));
		}
		localState.put(NOT_OUR_RESPONSE, msg.getRaw(MESSAGE_ID));

		if (msg.getBoolean(ACCEPT)) {
			localState.put(TIME, msg.getLong(TIME));
			localState.put(E_PUBLIC_KEY, msg.getRaw(E_PUBLIC_KEY));
			localState.put(TRANSPORT, msg.getDictionary(TRANSPORT));
		}
	}

	private void addAckData(BdfDictionary localState, BdfDictionary msg)
			throws FormatException {

		localState.put(MAC, msg.getRaw(MAC));
		localState.put(SIGNATURE, msg.getRaw(SIGNATURE));
	}

	private BdfDictionary getAckMessage(BdfDictionary localState)
			throws FormatException {

		BdfDictionary m = new BdfDictionary();
		m.put(TYPE, TYPE_ACK);
		m.put(GROUP_ID, localState.getRaw(GROUP_ID));
		m.put(SESSION_ID, localState.getRaw(SESSION_ID));
		m.put(MAC, localState.getRaw(OUR_MAC));
		m.put(SIGNATURE, localState.getRaw(OUR_SIGNATURE));
		return m;
	}

	private void logAction(IntroduceeProtocolState state,
			BdfDictionary localState, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		try {
			LOG.info("Sending " +
					(localState.getBoolean(ACCEPT) ? "accept " : "decline ") +
					"response in state " + state.name());
			LOG.info("Moving on to state " +
					getState(localState.getLong(STATE)).name());
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void logMessageReceived(IntroduceeProtocolState currentState,
			IntroduceeProtocolState nextState, BdfDictionary localState,
			int type, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		String t = "unknown";
		if (type == TYPE_REQUEST) t = "Introduction";
		else if (type == TYPE_RESPONSE) t = "Response";
		else if (type == TYPE_ACK) t = "ACK";
		else if (type == TYPE_ABORT) t = "Abort";

		LOG.info("Received " + t + " in state " + currentState.name());
		LOG.info("Moving on to state " + nextState.name());
	}

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onMessageDelivered(
			BdfDictionary localState, BdfDictionary delivered) {
		try {
			return noUpdate(localState);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private IntroduceeProtocolState getState(Long state) {
		return IntroduceeProtocolState.fromValue(state.intValue());
	}

	private Event getEvent(BdfDictionary localState, BdfDictionary msg)
			throws FormatException {

		ContactId contactId =
				new ContactId(localState.getLong(CONTACT_ID_1).intValue());
		AuthorId authorId = new AuthorId(localState.getRaw(REMOTE_AUTHOR_ID));

		SessionId sessionId = new SessionId(localState.getRaw(SESSION_ID));
		MessageId messageId = new MessageId(msg.getRaw(MESSAGE_ID));
		GroupId groupId = new GroupId(msg.getRaw(GROUP_ID));
		long time = msg.getLong(MESSAGE_TIME);
		String name = msg.getString(NAME);
		String message = msg.getOptionalString(MSG);
		boolean exists = localState.getBoolean(EXISTS);
		boolean introducesOtherIdentity =
				localState.getBoolean(REMOTE_AUTHOR_IS_US);

		IntroductionRequest ir = new IntroductionRequest(sessionId, messageId,
				groupId, ROLE_INTRODUCEE, time, false, false, false, false,
				authorId, name, false, message, false, exists,
				introducesOtherIdentity);
		return new IntroductionRequestReceivedEvent(contactId, ir);
	}

	private StateUpdate<BdfDictionary, BdfDictionary> abortSession(
			IntroduceeProtocolState currentState, BdfDictionary localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING))
			LOG.warning("Aborting protocol session in state " +
					currentState.name());

		localState.put(STATE, ERROR.getValue());
		localState.put(TASK, TASK_ABORT);
		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_ABORT);
		msg.put(GROUP_ID, localState.getRaw(GROUP_ID));
		msg.put(SESSION_ID, localState.getRaw(SESSION_ID));
		List<BdfDictionary> messages = Collections.singletonList(msg);

		// send abort event
		ContactId contactId =
				new ContactId(localState.getLong(CONTACT_ID_1).intValue());
		SessionId sessionId = new SessionId(localState.getRaw(SESSION_ID));
		Event event = new IntroductionAbortedEvent(contactId, sessionId);
		List<Event> events = Collections.singletonList(event);

		return new StateUpdate<BdfDictionary, BdfDictionary>(false, false,
				localState, messages, events);
	}

	private StateUpdate<BdfDictionary, BdfDictionary> noUpdate(
			BdfDictionary localState) throws FormatException {

		return new StateUpdate<BdfDictionary, BdfDictionary>(false, false,
				localState, Collections.<BdfDictionary>emptyList(),
				Collections.<Event>emptyList());
	}
}
