package org.briarproject.api;

import org.briarproject.api.event.Event;

import java.util.List;

public interface ProtocolEngine<A, S, M> {
	StateUpdate<S, M> onLocalAction(S localState, A action);

	StateUpdate<S, M> onMessageReceived(S localState, M received);

	StateUpdate<S, M> onMessageDelivered(S localState, M delivered);

	class StateUpdate<S, M> {
		public final boolean deleteMessages;
		public final boolean deleteState;
		public final S localState;
		public final List<M> toSend;
		public final List<Event> toBroadcast;

		public StateUpdate(boolean deleteMessages, boolean deleteState,
				S localState, List<M> toSend, List<Event> toBroadcast) {

			this.deleteMessages = deleteMessages;
			this.deleteState = deleteState;
			this.localState = localState;
			this.toSend = toSend;
			this.toBroadcast = toBroadcast;
		}
	}
}
