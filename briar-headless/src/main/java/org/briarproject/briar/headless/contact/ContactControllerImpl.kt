package org.briarproject.briar.headless.contact

import io.javalin.Context
import org.briarproject.bramble.api.contact.ContactManager
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class ContactControllerImpl
@Inject
constructor(private val contactManager: ContactManager) : ContactController {

    override fun list(ctx: Context): Context {
        val contacts = contactManager.activeContacts.map { contact ->
            contact.output()
        }
        return ctx.json(contacts)
    }

}
