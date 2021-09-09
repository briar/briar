package org.briarproject.briar.headless

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.http.Context
import io.javalin.http.util.ContextUtil
import io.mockk.mockk
import org.briarproject.bramble.api.connection.ConnectionRegistry
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.db.TransactionManager
import org.briarproject.bramble.api.identity.Author
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.identity.LocalAuthor
import org.briarproject.bramble.api.sync.Group
import org.briarproject.bramble.api.sync.Message
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.test.TestUtils.*
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.api.conversation.ConversationManager
import org.briarproject.briar.headless.event.WebSocketController
import org.skyscreamer.jsonassert.JSONAssert.assertEquals
import org.skyscreamer.jsonassert.JSONCompareMode.STRICT
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

abstract class ControllerTest {

    protected val db = mockk<TransactionManager>()
    protected val contactManager = mockk<ContactManager>()
    protected val conversationManager = mockk<ConversationManager>()
    protected val identityManager = mockk<IdentityManager>()
    protected val connectionRegistry = mockk<ConnectionRegistry>()
    protected val clock = mockk<Clock>()
    protected val ctx = mockk<Context>()

    protected val webSocketController = mockk<WebSocketController>()

    private val request = mockk<HttpServletRequest>(relaxed = true)
    private val response = mockk<HttpServletResponse>(relaxed = true)
    private val outputCtx = ContextUtil.init(request, response)

    protected val objectMapper = ObjectMapper()

    protected val group: Group = getGroup(getClientId(), 0)
    protected val author: Author = getAuthor()
    protected val localAuthor: LocalAuthor = getLocalAuthor()
    protected val contact: Contact = getContact(author, localAuthor.id, true)
    protected val message: Message = getMessage(group.id)
    protected val text: String = getRandomString(5)
    protected val timestamp = 42L
    protected val unreadCount = 42

    protected fun assertJsonEquals(json: String, obj: Any) {
        assertEquals(json, outputCtx.json(obj).resultString(), STRICT)
    }

}
