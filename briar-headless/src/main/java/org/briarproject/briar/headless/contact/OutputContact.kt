package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.identity.output

internal fun Contact.output() = mapOf(
    "contactId" to id.int,
    "author" to author.output(),
    "verified" to isVerified
)