package net.sf.briar.android.invitation;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.invitation.ConfirmationCallback;
import net.sf.briar.api.invitation.ConnectionCallback;
import net.sf.briar.api.invitation.InvitationManager;
import roboguice.activity.RoboActivity;

import com.google.inject.Inject;

public class AddContactActivity extends RoboActivity
implements ConnectionCallback, ConfirmationCallback {

	@Inject private CryptoComponent crypto;
	@Inject private InvitationManager invitationManager;

	// All of the following must be accessed on the UI thread
	private AddContactView view = null;
	private String networkName = null;
	private boolean useBluetooth = false;
	private int localInvitationCode = -1;
	private int localConfirmationCode = -1,  remoteConfirmationCode = -1;
	private ConfirmationCallback callback = null;
	private boolean localMatched = false;
	private boolean remoteCompared = false, remoteMatched = false;

	@Override
	public void onResume() {
		super.onResume();
		if(view == null) setView(new NetworkSetupView(this));
		else view.populate();
	}

	void setView(AddContactView view) {
		this.view = view;
		view.init(this);
		setContentView(view);
	}

	void setNetworkName(String networkName) {
		this.networkName = networkName;
	}

	String getNetworkName() {
		return networkName;
	}

	void setUseBluetooth(boolean useBluetooth) {
		this.useBluetooth = useBluetooth;
	}

	boolean getUseBluetooth() {
		return useBluetooth;
	}

	int generateLocalInvitationCode() {
		localInvitationCode = crypto.generateInvitationCode();
		return localInvitationCode;
	}

	int getLocalInvitationCode() {
		return localInvitationCode;
	}

	void remoteInvitationCodeEntered(int code) {
		setView(new ConnectionView(this));
		localMatched = remoteCompared = remoteMatched = false;
		invitationManager.connect(localInvitationCode, code, this);
	}

	int getLocalConfirmationCode() {
		return localConfirmationCode;
	}

	void remoteConfirmationCodeEntered(int code) {
		if(code == remoteConfirmationCode) {
			localMatched = true;
			if(remoteMatched) setView(new ContactAddedView(this));
			else if(remoteCompared) setView(new CodesDoNotMatchView(this));
			else setView(new WaitForContactView(this));
			callback.codesMatch();
		} else {
			setView(new CodesDoNotMatchView(this));
			callback.codesDoNotMatch();
		}
	}

	public void connectionEstablished(final int localCode, final int remoteCode,
			final ConfirmationCallback c) {
		runOnUiThread(new Runnable() {
			public void run() {
				localConfirmationCode = localCode;
				remoteConfirmationCode = remoteCode;
				callback = c;
				setView(new ConfirmationCodeView(AddContactActivity.this));
			}
		});
	}

	public void connectionNotEstablished() {
		runOnUiThread(new Runnable() {
			public void run() {
				setView(new ConnectionFailedView(AddContactActivity.this));
			}
		});
	}

	public void codesMatch() {
		runOnUiThread(new Runnable() {
			public void run() {
				remoteCompared = true;
				remoteMatched = true;
				if(localMatched)
					setView(new ContactAddedView(AddContactActivity.this));
			}
		});
	}

	public void codesDoNotMatch() {
		runOnUiThread(new Runnable() {
			public void run() {
				remoteCompared = true;
				if(localMatched)
					setView(new CodesDoNotMatchView(AddContactActivity.this));
			}
		});
	}
}
