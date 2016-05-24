package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ProtocolEngine;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.IntroductionAbortedEvent;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.introduction.IntroduceeAction;
import org.briarproject.api.introduction.IntroduceeProtocolState;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.introduction.IntroduceeAction.ACK;
import static org.briarproject.api.introduction.IntroduceeAction.LOCAL_ABORT;
import static org.briarproject.api.introduction.IntroduceeAction.LOCAL_ACCEPT;
import static org.briarproject.api.introduction.IntroduceeAction.LOCAL_DECLINE;
import static org.briarproject.api.introduction.IntroduceeAction.REMOTE_ABORT;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_ACK;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REMOTE_RESPONSE;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_RESPONSES;
import static org.briarproject.api.introduction.IntroduceeProtocolState.ERROR;
import static org.briarproject.api.introduction.IntroduceeProtocolState.FINISHED;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_MAC;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ACTIVATE_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ADD_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;

class IntroduceeEngine
		implements ProtocolEngine<BdfDictionary, IntroduceeSessionState, BdfDictionary> {

	private static final Logger LOG =
			Logger.getLogger(IntroduceeEngine.class.getName());

	@Override
	public StateUpdate<IntroduceeSessionState, BdfDictionary> onLocalAction(
			IntroduceeSessionState localState, BdfDictionary localAction) {

		try {
			IntroduceeProtocolState currentState = localState.getState();
			int type = localAction.getLong(TYPE).intValue();
			IntroduceeAction action;
			// FIXME: discuss? used to be: if has key ACCEPT:
			if (localState.wasAccepted()) action = IntroduceeAction
					.getLocal(type, localState.getAccept());
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
				localState.setState(nextState);
				localState.setAnswered(true);
				// create the introduction response message
				BdfDictionary msg = new BdfDictionary();
				msg.put(TYPE, TYPE_RESPONSE);
				msg.put(GROUP_ID, localState.getIntroductionGroupId());
				msg.put(SESSION_ID, localState.getSessionId());
				msg.put(ACCEPT, localState.getAccept());
				if (localState.getAccept()) {
					msg.put(TIME, localState.getOurTime());
					msg.put(E_PUBLIC_KEY, localState.getOurPublicKey());
					msg.put(TRANSPORT, localAction.getDictionary(TRANSPORT));
				}
				msg.put(MESSAGE_TIME, localAction.getLong(MESSAGE_TIME));
				messages.add(msg);
				logAction(currentState, localState, msg);

				if (nextState == AWAIT_ACK) {
					localState.setTask(TASK_ADD_CONTACT);
				}
			} else if (action == ACK) {
				// just send ACK, don't update local state again
				BdfDictionary ack = getAckMessage(localState);
				messages.add(ack);
			} else {
				throw new IllegalArgumentException();
			}
			List<Event> events = Collections.emptyList();
			return new StateUpdate<IntroduceeSessionState, BdfDictionary>(false,
					false,
					localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<IntroduceeSessionState, BdfDictionary> onMessageReceived(
			IntroduceeSessionState localState, BdfDictionary msg) {

		try {
			IntroduceeProtocolState currentState = localState.getState();
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
			localState.setState(nextState);
			List<BdfDictionary> messages;
			List<Event> events;
			// we received the introduction request
			if (currentState == AWAIT_REQUEST) {
				localState.setSessionId(new SessionId(msg.getRaw(SESSION_ID)));

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
				localState.setState(nextState);

				addResponseData(localState, msg);
				if (nextState == AWAIT_ACK) {
					localState.setTask(TASK_ADD_CONTACT);
//						messages = Collections
//								.singletonList(getAckMessage(localState));
				}
				messages = Collections.emptyList();
				events = Collections.emptyList();

			}
			// we already sent our ACK and now received the other one
			else if (currentState == AWAIT_ACK) {
				localState.setTask(TASK_ACTIVATE_CONTACT);
				addAckData(localState, msg);
				messages = Collections.emptyList();
				events = Collections.emptyList();
			}
			// we are done (probably declined response), ignore & delete message
			else if (currentState == FINISHED) {
				return new StateUpdate<IntroduceeSessionState, BdfDictionary>(true,
						false, localState,
						Collections.<BdfDictionary>emptyList(),
						Collections.<Event>emptyList());
			}
			// this should not happen
			else {
				throw new IllegalArgumentException();
			}
			return new StateUpdate<IntroduceeSessionState, BdfDictionary>(false, 
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void addRequestData(IntroduceeSessionState localState,
			BdfDictionary msg) throws FormatException {

		localState.setName(msg.getString(NAME));
		localState.setIntroducedPublicKey(msg.getRaw(PUBLIC_KEY));
		if (msg.containsKey(MSG)) {
			localState.setMessage(msg.getString(MSG));
		}
	}

	private void addResponseData(IntroduceeSessionState localState,
			BdfDictionary msg) throws FormatException {

		localState.setAccept(msg.getBoolean(ACCEPT));
		localState.setOtherResponseId(msg.getRaw(MESSAGE_ID));

		if (msg.getBoolean(ACCEPT)) {
			localState.setTheirTime(msg.getLong(TIME));
			localState.setEPublicKey(msg.getRaw(E_PUBLIC_KEY));
			localState.setTransport(msg.getDictionary(TRANSPORT));
		}
	}

	private void addAckData(IntroduceeSessionState localState, BdfDictionary msg)
			throws FormatException {

		localState.setMac(msg.getRaw(MAC));
		localState.setSignature(msg.getRaw(SIGNATURE));
	}

	private BdfDictionary getAckMessage(IntroduceeSessionState localState)
			throws FormatException {

		BdfDictionary m = new BdfDictionary();
		m.put(TYPE, TYPE_ACK);
		m.put(MAC, localState.getOurMac());
		m.put(SIGNATURE, localState.getOurSignature());
		m.put(GROUP_ID, localState.getIntroductionGroupId());
		m.put(SESSION_ID, localState.getSessionId());

		if (LOG.isLoggable(INFO)) {
			LOG.info("Sending ACK " + " to " +
					localState.getIntroducerName() + " for " +
					localState.getName() +
					" with session ID " +
					Arrays.hashCode(m.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(m.getRaw(GROUP_ID)));
		}
		return m;
	}

	private void logAction(IntroduceeProtocolState state,
			IntroduceeSessionState localState, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		try {
			LOG.info("Sending " +
					(localState.getAccept() ? "accept " : "decline ") +
					"response in state " + state.name() +
					" to " + localState.getName() +
					" for " + localState.getIntroducerName() +
					" with session ID " +
					Arrays.hashCode(msg.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(msg.getRaw(GROUP_ID)) + ". " +
					"Moving on to state " +
					localState.getState().name()
			);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void logMessageReceived(IntroduceeProtocolState currentState,
			IntroduceeProtocolState nextState, 
			IntroduceeSessionState localState, int type, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		try {
			String t = "unknown";
			if (type == TYPE_REQUEST) t = "Introduction";
			else if (type == TYPE_RESPONSE) t = "Response";
			else if (type == TYPE_ACK) t = "ACK";
			else if (type == TYPE_ABORT) t = "Abort";

			LOG.info("Received " + t + " in state " + currentState.name() +
					" from " + localState.getIntroducerName() +
					(localState.getName() != null ?
							" related to " + localState.getName() : "") +
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
	public StateUpdate<IntroduceeSessionState, BdfDictionary> onMessageDelivered(
			IntroduceeSessionState localState, BdfDictionary delivered) {
		try {
			return noUpdate(localState);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private Event getEvent(IntroduceeSessionState localState, BdfDictionary msg)
			throws FormatException {

		ContactId contactId = localState.getIntroducerId();
		AuthorId authorId = localState.getRemoteAuthorId();
		SessionId sessionId = localState.getSessionId();
		MessageId messageId = new MessageId(msg.getRaw(MESSAGE_ID));
		long time = msg.getLong(MESSAGE_TIME);
		String name = msg.getString(NAME);
		String message = msg.getOptionalString(MSG);
		boolean exists = localState.getContactExists();
		boolean introducesOtherIdentity = localState.getRemoteAuthorIsUs();

		IntroductionRequest ir = new IntroductionRequest(sessionId, messageId,
				ROLE_INTRODUCEE, time, false, false, false, false, authorId,
				name, false, message, false, exists, introducesOtherIdentity);
		return new IntroductionRequestReceivedEvent(contactId, ir);
	}

	private StateUpdate<IntroduceeSessionState, BdfDictionary> abortSession(
			IntroduceeProtocolState currentState, 
			IntroduceeSessionState localState) throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					Arrays.hashCode(localState.getSessionId().getBytes()) +
					" in state " + currentState.name()
					);
		}

		localState.setState(ERROR);
		localState.setTask(TASK_ABORT);
		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_ABORT);
		msg.put(GROUP_ID, localState.getIntroductionGroupId());
		msg.put(SESSION_ID, localState.getSessionId());
		List<BdfDictionary> messages = Collections.singletonList(msg);

		// send abort event
		ContactId contactId = localState.getIntroducerId();
		SessionId sessionId = localState.getSessionId();
		Event event = new IntroductionAbortedEvent(contactId, sessionId);
		List<Event> events = Collections.singletonList(event);

		return new StateUpdate<IntroduceeSessionState, BdfDictionary>(false, 
				false, localState, messages, events);
	}

	private StateUpdate<IntroduceeSessionState, BdfDictionary> noUpdate(
			IntroduceeSessionState localState) throws FormatException {

		return new StateUpdate<IntroduceeSessionState, BdfDictionary>(false, false,
				localState, Collections.<BdfDictionary>emptyList(),
				Collections.<Event>emptyList());
	}
}
