package org.briarproject.briar.headless

import io.javalin.Context
import io.javalin.core.util.ContextUtil
import io.mockk.mockk
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.identity.Author
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.identity.LocalAuthor
import org.briarproject.bramble.api.sync.Group
import org.briarproject.bramble.api.sync.Message
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.test.TestUtils.*
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.skyscreamer.jsonassert.JSONAssert.assertEquals
import org.skyscreamer.jsonassert.JSONCompareMode.STRICT
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

abstract class ControllerTest {

    protected val contactManager = mockk<ContactManager>()
    protected val identityManager = mockk<IdentityManager>()
    protected val clock = mockk<Clock>()
    protected val ctx = mockk<Context>()

    private val request = mockk<HttpServletRequest>(relaxed = true)
    private val response = mockk<HttpServletResponse>(relaxed = true)
    private val outputCtx = ContextUtil.init(request, response)

    protected val group: Group = getGroup(getClientId(), 0)
    protected val author: Author = getAuthor()
    protected val localAuthor: LocalAuthor = getLocalAuthor()
    protected val contact = Contact(ContactId(1), author, localAuthor.id, true, true)
    protected val message: Message = getMessage(group.id)
    protected val text: String = getRandomString(5)
    protected val timestamp = 42L

    protected fun assertJsonEquals(json: String, obj: Any) {
        assertEquals(json, outputCtx.json(obj).resultString(), STRICT)
    }

}
