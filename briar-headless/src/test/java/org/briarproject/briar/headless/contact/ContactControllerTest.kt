package org.briarproject.briar.headless.contact

import io.javalin.NotFoundResponse
import io.javalin.json.JavalinJson.toJson
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.contact.PendingContactId
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.api.db.NoSuchPendingContactException
import org.briarproject.bramble.identity.output
import org.briarproject.bramble.test.TestUtils.getPendingContact
import org.briarproject.bramble.test.TestUtils.getRandomBytes
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.json.JsonDict
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class ContactControllerTest : ControllerTest() {

    private val controller = ContactControllerImpl(contactManager, objectMapper)
    private val pendingContact = getPendingContact()

    @Test
    fun testEmptyContactList() {
        every { contactManager.contacts } returns emptyList<Contact>()
        every { ctx.json(emptyList<Any>()) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testList() {
        every { contactManager.contacts } returns listOf(contact)
        every { ctx.json(listOf(contact.output())) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testLink() {
        val link = "briar://link"
        every { contactManager.handshakeLink } returns link
        every { ctx.json(JsonDict("link" to link)) } returns ctx
        controller.link(ctx)
    }

    @Test
    fun testAddPendingContact() {
        val link = "briar://link123"
        val alias = "Alias123"
        val body = """{
            "link": "$link",
            "alias": "$alias"
        }"""
        every { ctx.body() } returns body
        every { contactManager.addPendingContact(link, alias) } returns pendingContact
        every { ctx.json(pendingContact.output()) } returns ctx
        controller.addPendingContact(ctx)
    }

    @Test
    fun testListPendingContacts() {
        every { contactManager.pendingContacts } returns listOf(pendingContact)
        every { ctx.json(listOf(pendingContact.output())) } returns ctx
        controller.listPendingContacts(ctx)
    }

    @Test
    fun testRemovePendingContact() {
        val id = pendingContact.id
        every { ctx.body() } returns """{"pendingContactId": ${toJson(id.bytes)}}"""
        every { contactManager.removePendingContact(id) } just Runs
        controller.removePendingContact(ctx)
    }

    @Test
    fun testRemovePendingContactInvalidId() {
        every { ctx.body() } returns """{"pendingContactId": "foo"}"""
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
    }

    @Test
    fun testRemovePendingContactTooShortId() {
        val bytes = getRandomBytes(PendingContactId.LENGTH - 1)
        every { ctx.body() } returns """{"pendingContactId": ${toJson(bytes)}}"""
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
    }

    @Test
    fun testRemovePendingContactTooLongId() {
        val bytes = getRandomBytes(PendingContactId.LENGTH + 1)
        every { ctx.body() } returns """{"pendingContactId": ${toJson(bytes)}}"""
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
    }

    @Test
    fun testRemovePendingContactNonexistentId() {
        val id = pendingContact.id
        every { ctx.body() } returns """{"pendingContactId": ${toJson(id.bytes)}}"""
        every { contactManager.removePendingContact(id) } throws NoSuchPendingContactException()
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
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

    @Test
    fun testOutputPendingContact() {
        val json = """
            {
                "pendingContactId": ${toJson(pendingContact.id.bytes)},
                "alias": "${pendingContact.alias}",
                "state": "${pendingContact.state.name.toLowerCase()}",
                "timestamp": ${pendingContact.timestamp}
            }
        """
        assertJsonEquals(json, pendingContact.output())
    }

}
