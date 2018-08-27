package org.briarproject.briar.headless.contact

import io.javalin.json.JavalinJson.toJson
import io.mockk.every
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.output
import org.junit.jupiter.api.Test

internal class ContactControllerTest : ControllerTest() {

    private val controller = ContactController(contactManager)

    @Test
    fun testEmptyContactList() {
        every { contactManager.activeContacts } returns emptyList<Contact>()
        every { ctx.json(emptyList<OutputContact>()) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testList() {
        every { contactManager.activeContacts } returns listOf(contact)
        every { ctx.json(listOf(contact.output())) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testOutputContact() {
        val json = """
            {
                "id": ${contact.id.int},
                "author": ${toJson(author.output())},
                "verified": ${contact.isVerified}
            }
        """
        assertJsonEquals(json, contact.output())
    }

    @Test
    fun testOutputAuthor() {
        val json = """
            {
                "id": ${toJson(author.id.bytes)},
                "name": "${author.name}",
                "publicKey": ${toJson(author.publicKey)}
            }
        """
        assertJsonEquals(json, author.output())
    }

}
