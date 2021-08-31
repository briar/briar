package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.HandshakeLinkConstants.BASE32_LINK_BYTES
import org.briarproject.briar.headless.IntegrationTest
import org.briarproject.briar.headless.url
import org.briarproject.briar.test.BriarTestUtils.getRealHandshakeLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContactControllerIntegrationTest : IntegrationTest() {

    @Test
    fun `returning list of contacts needs authentication token`() {
        val response = getWithWrongToken("$url/contacts")
        assertEquals(401, response.statusCode)
    }

    @Test
    fun `returns list of contacts`() {
        // retrieve empty list of contacts
        var response = get("$url/contacts")
        assertEquals(200, response.statusCode)
        assertEquals(0, response.jsonArray.length())

        // add one test contact
        val testContactName = "testContactName"
        testDataCreator.addContact(testContactName, true, false)

        // retrieve list with one test contact
        response = get("$url/contacts")
        assertEquals(200, response.statusCode)
        assertEquals(1, response.jsonArray.length())
        val contact = response.jsonArray.getJSONObject(0)
        val author = contact.getJSONObject("author")
        assertEquals(testContactName, author.getString("name"))
    }

    @Test
    fun `returns own handshake link`() {
        val response = get("$url/contacts/add/link")
        assertEquals(200, response.statusCode)
        val link = response.jsonObject.getString("link")
        assertTrue(link.startsWith("briar://"))
        assertEquals(BASE32_LINK_BYTES + 8, link.length)
    }

    @Test
    fun `returning own handshake link needs authentication token`() {
        val response = getWithWrongToken("$url/contacts/add/link")
        assertEquals(401, response.statusCode)
    }

    @Test
    fun `returns list of pending contacts`() {
        // retrieve empty list of pending contacts
        var response = get("$url/contacts/add/pending")
        assertEquals(200, response.statusCode)
        assertEquals(0, response.jsonArray.length())

        // add one pending contact
        val alias = "AliasFoo"
        val json = """{
            "link": "${getRealHandshakeLink(crypto)}",
            "alias": "$alias"
        }"""
        response = post("$url/contacts/add/pending", json)
        assertEquals(200, response.statusCode)

        // get added contact as only list item
        response = get("$url/contacts/add/pending")
        assertEquals(200, response.statusCode)
        assertEquals(1, response.jsonArray.length())
        val jsonObject = response.jsonArray.getJSONObject(0)
        assertEquals(alias, jsonObject.getJSONObject("pendingContact").getString("alias"))

        // remove pending contact again
        val idString = jsonObject.getJSONObject("pendingContact").getString("pendingContactId")
        val deleteJson = """{"pendingContactId": "$idString"}"""
        response = delete("$url/contacts/add/pending", deleteJson)
        assertEquals(200, response.statusCode)

        // list of pending contacts should be empty now
        response = get("$url/contacts/add/pending")
        assertEquals(200, response.statusCode)
        assertEquals(0, response.jsonArray.length())
    }

    @Test
    fun `returning list of pending contacts needs authentication token`() {
        val response = getWithWrongToken("$url/contacts/add/pending")
        assertEquals(401, response.statusCode)
    }

    @Test
    fun `adding a pending contact needs authentication token`() {
        val response = postWithWrongToken("$url/contacts/add/pending")
        assertEquals(401, response.statusCode)
    }

    @Test
    fun `adding a pending contact with invalid link`() {
        val alias = "AliasFoo"
        val json = """{
            "link": "briar://invalid",
            "alias": "$alias"
        }"""
        val response = post("$url/contacts/add/pending", json)
        assertEquals(400, response.statusCode)
        assertEquals("INVALID_LINK", response.jsonObject.getString("error"))
    }

    @Test
    fun `adding a pending contact with invalid public key`() {
        val alias = "AliasFoo"
        val json = """{
            "link": "briar://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "alias": "$alias"
        }"""
        val response = post("$url/contacts/add/pending", json)
        assertEquals(400, response.statusCode)
        assertEquals("INVALID_PUBLIC_KEY", response.jsonObject.getString("error"))
    }

    @Test
    fun `adding a pending contact that already exists as pending contact`() {
        val alias = "AliasFoo"
        val json = """{
            "link": "${getRealHandshakeLink(crypto)}",
            "alias": "$alias"
        }"""
        var response = post("$url/contacts/add/pending", json)
        assertEquals(200, response.statusCode)

        val pendingContactId = response.jsonObject.getString("pendingContactId")

        response = post("$url/contacts/add/pending", json)
        assertEquals(403, response.statusCode)
        assertEquals("PENDING_EXISTS", response.jsonObject.getString("error"))
        assertEquals(pendingContactId, response.jsonObject.getString("pendingContactId"))
        assertEquals(alias, response.jsonObject.getString("pendingContactAlias"))
    }

    @Test
    fun `removing a pending contact needs authentication token`() {
        val response = deleteWithWrongToken("$url/contacts/add/pending")
        assertEquals(401, response.statusCode)
    }

    @Test
    fun `deleting a contact needs authentication token`() {
        val response = deleteWithWrongToken("$url/contacts/1")
        assertEquals(401, response.statusCode)
    }

    @Test
    fun `deleting real and non-existing contact`() {
        var response = delete("$url/contacts/1")
        assertEquals(200, response.statusCode)

        response = delete("$url/contacts/1")
        assertEquals(404, response.statusCode)
    }

}
