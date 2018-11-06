package org.briarproject.briar.headless.contact

import org.briarproject.briar.headless.IntegrationTest
import org.briarproject.briar.headless.url
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContactControllerIntegrationTest: IntegrationTest() {

    @Test
    fun `list of contacts need authentication token`() {
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
        val testContactName= "testContactName"
        testDataCreator.addContact(testContactName)

        // retrieve list with one test contact
        response = get("$url/contacts")
        assertEquals(200, response.statusCode)
        assertEquals(1, response.jsonArray.length())
        val contact = response.jsonArray.getJSONObject(0)
        val author = contact.getJSONObject("author")
        assertEquals(testContactName, author.getString("name"))
    }

    @Test
    fun `deleting contact need authentication token`() {
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
