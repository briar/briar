package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.Contact

internal fun Contact.output() = OutputContact(this)
