package org.briarproject.briar.android.contact.add.nearby;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

abstract class ContactAddingState {

	static class KeyAgreementListening extends ContactAddingState {
		final Bitmap qrCode;

		KeyAgreementListening(Bitmap qrCode) {
			this.qrCode = qrCode;
		}
	}

	static class QrCodeScanned extends ContactAddingState {
	}

	static class KeyAgreementWaiting extends ContactAddingState {
	}

	static class KeyAgreementStarted extends ContactAddingState {
	}

	static class ContactExchangeStarted extends ContactAddingState {
	}

	static class ContactExchangeFinished extends ContactAddingState {
		final ContactExchangeResult result;

		ContactExchangeFinished(ContactExchangeResult result) {
			this.result = result;
		}
	}

	static class Failed extends ContactAddingState {
		/**
		 * Non-null if failed due to the scanned QR code version.
		 * True if the app producing the code is too old.
		 * False if the scanning app is too old.
		 */
		@Nullable
		final Boolean qrCodeTooOld;

		Failed(@Nullable Boolean qrCodeTooOld) {
			this.qrCodeTooOld = qrCodeTooOld;
		}

		Failed() {
			this(null);
		}
	}

}
