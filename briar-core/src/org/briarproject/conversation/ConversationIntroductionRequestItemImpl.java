package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationIntroductionRequestItem;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.introduction.IntroductionRequest;

class ConversationIntroductionRequestItemImpl {

	static ConversationItem from(IntroductionRequest i) {
		return i.isLocal() ? new Outgoing(i) : new Incoming(i);
	}

	static class Outgoing extends OutgoingConversationItem
			implements ConversationIntroductionRequestItem {

		private final IntroductionRequest ir;
		private boolean answered;

		public Outgoing(IntroductionRequest ir) {
			super(ir.getMessageId(), ir.getTimestamp(), ir.isSent(), ir.isSeen());

			this.ir = ir;
			this.answered = ir.wasAnswered();
		}

		@Override
		public IntroductionRequest getIntroductionRequest() {
			return ir;
		}

		@Override
		public boolean wasAnswered() {
			return answered;
		}

		@Override
		public void setAnswered(boolean answered) {
			this.answered = answered;
		}
	}

	// This class is not thread-safe
	static class Incoming extends IncomingConversationItem
			implements ConversationIntroductionRequestItem {

		private final IntroductionRequest ir;
		private boolean answered;

		public Incoming(IntroductionRequest ir) {
			super(ir.getMessageId(), ir.getTimestamp(), ir.isRead());

			this.ir = ir;
			this.answered = ir.wasAnswered();
		}

		@Override
		public IntroductionRequest getIntroductionRequest() {
			return ir;
		}

		@Override
		public boolean wasAnswered() {
			return answered;
		}

		@Override
		public void setAnswered(boolean answered) {
			this.answered = answered;
		}
	}
}
