package net.sf.briar.android.invitation;

import android.content.Context;

interface InvitationManager {

	int TIMEOUT = 20 * 1000;

	void tryToConnect(ConnectionListener listener);

	String getLocalInvitationCode();

	String getRemoteInvitationCode();

	void setRemoteInvitationCode(String code);

	void startWifiConnectionWorker(Context ctx);

	void startBluetoothConnectionWorker(Context ctx);

	String getLocalConfirmationCode();

	String getRemoteConfirmationCode();

	void startConfirmationWorker(ConfirmationListener listener);
}
