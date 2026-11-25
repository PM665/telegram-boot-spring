package sample

private val markdownSpecialCharacters = Regex("([_*`\\[])")

/** Replies are sent with Markdown, so values coming from users must be escaped. */
fun String.escapeMarkdown(): String = replace(markdownSpecialCharacters, "\\\\$1")
