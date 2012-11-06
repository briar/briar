package net.sf.briar.android.invitation;

import android.content.Context;
import android.util.Log;

class InvitationManagerImpl implements InvitationManager {

	public void tryToConnect(final ConnectionListener listener) {
		new Thread() {
			@Override
			public void run() {
				try {
					// FIXME
					Thread.sleep((long) (Math.random() * TIMEOUT));
					if(Math.random() < 0.5) listener.connectionEstablished();
					else listener.connectionNotEstablished();
				} catch(InterruptedException e) {
					Log.w(getClass().getName(), e.toString());
					listener.connectionNotEstablished();
				}
			}
		}.start();
	}

	public String getLocalInvitationCode() {
		// FIXME
		return "123456";
	}

	public String getRemoteInvitationCode() {
		// FIXME
		return "123456";
	}

	public void setRemoteInvitationCode(String code) {
		// FIXME
	}

	public void startWifiConnectionWorker(Context ctx) {
		// FIXME
	}

	public void startBluetoothConnectionWorker(Context ctx) {
		// FIXME
	}

	public String getLocalConfirmationCode() {
		// FIXME
		return "123456";
	}

	public String getRemoteConfirmationCode() {
		// FIXME
		return "123456";
	}

	public void startConfirmationWorker(ConfirmationListener listener) {
		// FIXME
		try {
			Thread.sleep(1000 + (int) (Math.random() * 4 * 1000));
		} catch(InterruptedException e) {
			Log.w(getClass().getName(), e.toString());
			Thread.currentThread().interrupt();
		}
		listener.confirmationReceived();
	}
}
