package org.briarproject.briar.android.contact.add.nearby;

import android.graphics.Bitmap;

import org.briarproject.bramble.api.identity.Author;

import androidx.annotation.Nullable;

abstract class AddContactState {

	static class KeyAgreementListening extends AddContactState {
		final Bitmap qrCode;

		KeyAgreementListening(Bitmap qrCode) {
			this.qrCode = qrCode;
		}
	}

	static class QrCodeScanned extends AddContactState {
	}

	static class KeyAgreementWaiting extends AddContactState {
	}

	static class KeyAgreementStarted extends AddContactState {
	}

	static class ContactExchangeStarted extends AddContactState {
	}

	static class ContactExchangeFinished extends AddContactState {
		final ContactExchangeResult result;

		ContactExchangeFinished(ContactExchangeResult result) {
			this.result = result;
		}
	}

	static class Failed extends AddContactState {
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

	abstract static class ContactExchangeResult {
		static class Success extends ContactExchangeResult {
			final Author remoteAuthor;

			Success(Author remoteAuthor) {
				this.remoteAuthor = remoteAuthor;
			}
		}

		static class Error extends ContactExchangeResult {
			@Nullable
			final Author duplicateAuthor;

			Error(@Nullable Author duplicateAuthor) {
				this.duplicateAuthor = duplicateAuthor;
			}
		}
	} // end ContactExchangeResult

}
