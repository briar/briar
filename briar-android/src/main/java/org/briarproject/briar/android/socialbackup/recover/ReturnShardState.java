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

	static class SocialBackupExchangeStarted extends ReturnShardState {
	}

	static class SocialBackupExchangeFinished extends ReturnShardState {
		final SocialBackupExchangeResult
				result;

		SocialBackupExchangeFinished(
				SocialBackupExchangeResult result) {
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

	abstract static class SocialBackupExchangeResult {
		static class Success extends SocialBackupExchangeResult {
			Success() {}
		}

		static class Error extends SocialBackupExchangeResult {
			@Nullable
			final Author duplicateAuthor;

			Error(@Nullable Author duplicateAuthor) {
				this.duplicateAuthor = duplicateAuthor;
			}
		}
	} // end ContactExchangeResult

}
