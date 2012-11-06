package net.sf.briar.android.invitation;

class InvitationManagerFactory {

	private static final Object LOCK = new Object();
	private static InvitationManager instance = null; // Locking: lock

	static InvitationManager getInvitationManager() {
		synchronized(LOCK) {
			if(instance == null) instance = new InvitationManagerImpl();
			return instance;
		}
	}
}
