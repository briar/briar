package org.briarproject.briar.headless.blogs

import org.briarproject.briar.api.blog.BlogPostHeader

internal fun BlogPostHeader.output(body: String) = OutputBlogPost(this, body)
