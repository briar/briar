package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.Contact
import org.briarproject.briar.headless.output
import javax.annotation.concurrent.Immutable

@Immutable
@Suppress("unused")
internal class OutputContact(c: Contact) {

    val id = c.id.int
    val author = c.author.output()
    val verified = c.isVerified

}
