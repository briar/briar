package org.briarproject.briar.headless.contact

import io.javalin.NotFoundResponse
import io.javalin.json.JavalinJson.toJson
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.identity.output
import org.briarproject.briar.headless.ControllerTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class ContactControllerTest : ControllerTest() {

    private val controller = ContactControllerImpl(contactManager)

    @Test
    fun testEmptyContactList() {
        every { contactManager.activeContacts } returns emptyList<Contact>()
        every { ctx.json(emptyList<Any>()) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testList() {
        every { contactManager.activeContacts } returns listOf(contact)
        every { ctx.json(listOf(contact.output())) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testDelete() {
        every { ctx.pathParam("contactId") } returns "1"
        every { contactManager.removeContact(ContactId(1)) } just Runs
        controller.delete(ctx)
    }

    @Test
    fun testDeleteInvalidContactId() {
        every { ctx.pathParam("contactId") } returns "foo"
        assertThrows(NotFoundResponse::class.java) {
            controller.delete(ctx)
        }
    }

    @Test
    fun testDeleteNonexistentContactId() {
        every { ctx.pathParam("contactId") } returns "1"
        every { contactManager.removeContact(ContactId(1)) } throws NoSuchContactException()
        assertThrows(NotFoundResponse::class.java) {
            controller.delete(ctx)
        }
    }

    @Test
    fun testOutputContact() {
        val json = """
            {
                "contactId": ${contact.id.int},
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
                "formatVersion": 1,
                "id": ${toJson(author.id.bytes)},
                "name": "${author.name}",
                "publicKey": ${toJson(author.publicKey)}
            }
        """
        assertJsonEquals(json, author.output())
    }

}
