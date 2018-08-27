package org.briarproject.briar.headless.blogs

import io.javalin.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifySequence
import org.briarproject.bramble.api.identity.Author.Status.OURSELVES
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.test.TestUtils.*
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.api.blog.Blog
import org.briarproject.briar.api.blog.BlogManager
import org.briarproject.briar.api.blog.BlogPostFactory
import org.briarproject.briar.api.blog.BlogPostHeader
import org.briarproject.briar.api.blog.MessageType.POST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlogControllerTest {

    private val blogManager = mockk<BlogManager>()
    private val blogPostFactory = mockk<BlogPostFactory>()
    private val identityManager = mockk<IdentityManager>()
    private val clock = mockk<Clock>()
    private val ctx = mockk<Context>()

    private val blogController =
            BlogController(blogManager, blogPostFactory, identityManager, clock)

    private val group = getGroup(getClientId(), 0)
    private val author = getAuthor()
    private val blog = Blog(group, author, false)
    private val message = getMessage(group.id)
    private val body = getRandomString(5)

    @Test
    fun testList() {
        val header = BlogPostHeader(POST, group.id, message.id, null, 0, 0, author, OURSELVES, true,
                true)
        val slot = slot<List<OutputBlogPost>>()

        every { blogManager.blogs } returns listOf(blog)
        every { blogManager.getPostHeaders(any()) } returns listOf(header)
        every { blogManager.getPostBody(any()) } returns body
        every { ctx.json(capture(slot)) } returns ctx

        blogController.listPosts(ctx)

        assertEquals(1, slot.captured.size)
        assertEquals(header.id.bytes, slot.captured[0].id)
        assertEquals(body, slot.captured[0].body)

        verifySequence {
            blogManager.blogs
            blogManager.getPostHeaders(group.id)
            blogManager.getPostBody(message.id)
            ctx.json(slot.captured)
        }
    }

}
