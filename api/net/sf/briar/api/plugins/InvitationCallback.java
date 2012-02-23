package net.sf.briar.api.plugins;

public interface InvitationCallback {

	boolean isCancelled();

	int enterConfirmationCode(int code);

	void showProgress(String... message);

	void showFailure(String... message);

	void showSuccess();
}
