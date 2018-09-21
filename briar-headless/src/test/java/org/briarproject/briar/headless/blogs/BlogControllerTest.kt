package org.briarproject.briar.headless.blogs

import io.javalin.BadRequestResponse
import io.javalin.json.JavalinJson.toJson
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.briarproject.bramble.api.identity.Author.Status.OURSELVES
import org.briarproject.bramble.api.sync.MessageId
import org.briarproject.bramble.identity.output
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.api.blog.*
import org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_POST_BODY_LENGTH
import org.briarproject.briar.api.blog.MessageType.POST
import org.briarproject.briar.headless.ControllerTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class BlogControllerTest : ControllerTest() {

    private val blogManager = mockk<BlogManager>()
    private val blogPostFactory = mockk<BlogPostFactory>()

    private val controller =
        BlogControllerImpl(blogManager, blogPostFactory, identityManager, clock)

    private val blog = Blog(group, author, false)
    private val parentId: MessageId? = null
    private val rssFeed = false
    private val read = true
    private val header = BlogPostHeader(
        POST,
        group.id,
        message.id,
        parentId,
        message.timestamp,
        timestamp,
        author,
        OURSELVES,
        rssFeed,
        read
    )

    @Test
    fun testCreate() {
        val post = BlogPost(message, null, localAuthor)

        every { ctx.formParam("text") } returns body
        every { identityManager.localAuthor } returns localAuthor
        every { blogManager.getPersonalBlog(localAuthor) } returns blog
        every { clock.currentTimeMillis() } returns message.timestamp
        every {
            blogPostFactory.createBlogPost(
                message.groupId,
                message.timestamp,
                parentId,
                localAuthor,
                body
            )
        } returns post
        every { blogManager.addLocalPost(post) } just Runs
        every { blogManager.getPostHeader(post.message.groupId, post.message.id) } returns header
        every { ctx.json(header.output(body)) } returns ctx

        controller.createPost(ctx)
    }

    @Test
    fun testCreateNoText() {
        every { ctx.formParam("text") } returns null

        assertThrows(BadRequestResponse::class.java) { controller.createPost(ctx) }
    }

    @Test
    fun testCreateEmptyText() {
        every { ctx.formParam("text") } returns ""

        assertThrows(BadRequestResponse::class.java) { controller.createPost(ctx) }
    }

    @Test
    fun testCreateTooLongText() {
        every { ctx.formParam("text") } returns getRandomString(MAX_BLOG_POST_BODY_LENGTH + 1)

        assertThrows(BadRequestResponse::class.java) { controller.createPost(ctx) }
    }

    @Test
    fun testList() {
        every { blogManager.blogs } returns listOf(blog)
        every { blogManager.getPostHeaders(group.id) } returns listOf(header)
        every { blogManager.getPostBody(message.id) } returns body
        every { ctx.json(listOf(header.output(body))) } returns ctx

        controller.listPosts(ctx)
    }

    @Test
    fun testEmptyList() {
        every { blogManager.blogs } returns listOf(blog)
        every { blogManager.getPostHeaders(group.id) } returns emptyList()
        every { ctx.json(emptyList<OutputBlogPost>()) } returns ctx

        controller.listPosts(ctx)
    }

    @Test
    fun testOutputBlogPost() {
        val json = """
            {
                "body": "$body",
                "author": ${toJson(author.output())},
                "authorStatus": "ourselves",
                "type": "post",
                "id": ${toJson(header.id.bytes)},
                "parentId": $parentId,
                "read": $read,
                "rssFeed": $rssFeed,
                "timestamp": ${message.timestamp},
                "timestampReceived": $timestamp
            }
        """
        assertJsonEquals(json, header.output(body))
    }

}
