package org.briarproject.briar.android.controller

import org.briarproject.bramble.api.system.Wakeful
import org.briarproject.briar.android.controller.handler.ResultHandler

interface BriarController : ActivityLifecycleController {

	fun startAndBindService()

	fun accountSignedIn(): Boolean

	fun isTelegramConnectorReady(): Boolean

	fun getTelegramLinkedIdentity(handler: ResultHandler<String>)

	/**
	 * Returns true via handler when app has dozed
	 * without being white-listed.
	 */
	fun hasDozed(handler: ResultHandler<Boolean>)

	fun doNotAskAgainForDozeWhiteListing()

	@Wakeful
	fun signOut(handler: ResultHandler<Void>, deleteAccount: Boolean)

	fun deleteAccount()

}
