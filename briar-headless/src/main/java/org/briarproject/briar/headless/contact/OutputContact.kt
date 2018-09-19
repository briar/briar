package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.identity.OutputAuthor
import org.briarproject.bramble.identity.output
import javax.annotation.concurrent.Immutable

@Immutable
internal data class OutputContact(
    val contactId: Int,
    val author: OutputAuthor,
    val verified: Boolean
) {
    internal constructor(c: Contact) : this(
        contactId = c.id.int,
        author = c.author.output(),
        verified = c.isVerified
    )
}

internal fun Contact.output() = OutputContact(this)
