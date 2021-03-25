package org.briarproject.briar.android.socialbackup.recover;

import android.graphics.Bitmap;

import org.briarproject.bramble.api.identity.Author;

import androidx.annotation.Nullable;

abstract class ReturnShardState {

	static class KeyAgreementListening extends
			ReturnShardState {
		final Bitmap qrCode;

		KeyAgreementListening(Bitmap qrCode) {
			this.qrCode = qrCode;
		}
	}

	static class QrCodeScanned extends
			ReturnShardState {
	}

	static class KeyAgreementWaiting extends ReturnShardState {
	}

	static class KeyAgreementStarted extends ReturnShardState {
	}

	static class ContactExchangeStarted extends ReturnShardState {
	}

	static class ContactExchangeFinished extends ReturnShardState {
		final ContactExchangeResult
				result;

		ContactExchangeFinished(
				ContactExchangeResult result) {
			this.result = result;
		}
	}

	static class Failed extends ReturnShardState {
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
