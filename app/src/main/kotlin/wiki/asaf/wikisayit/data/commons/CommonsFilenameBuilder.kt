package wiki.asaf.wikisayit.data.commons

/** MediaWiki title syntax forbids these regardless of namespace/config. */
private val ILLEGAL_TITLE_CHARS = charArrayOf('#', '<', '>', '[', ']', '|', '{', '}')

/** Safe upper bound for a Commons file title (incl. extension); the hard cap is 255 bytes UTF-8. */
private const val MAX_TITLE_BYTES = 240

private const val EXTENSION = ".ogg"

/**
 * Builds a Commons filename per the product spec: `<iso>-<QID|LID>-<label>-<username>.ogg`
 * (e.g. `uk-L708539-мова-Ijon.ogg`, `he-Q432522-יונתן רטוש-Ijon.ogg`). When [speakerName] is
 * non-blank — the speaker differs from the Wikimedia account name, e.g. a shared account — it's
 * appended after the username: `<iso>-<QID|LID>-<label>-<username>-<speakerName>.ogg`.
 *
 * [label] and [speakerName] are sanitized for MediaWiki title syntax and, if the result would
 * exceed Commons' title length cap, [label] is truncated — the iso code, entity id, username,
 * and extension never are, since those are what make the filename findable and attributable.
 */
fun buildCommonsFilename(
    isoCode: String,
    entityId: String,
    label: String,
    username: String,
    speakerName: String = "",
): String {
    val prefix = "$isoCode-$entityId-"
    val sanitizedSpeakerName = sanitizeTitleLabel(speakerName)
    val suffix =
        if (sanitizedSpeakerName.isNotBlank()) {
            "-$username-$sanitizedSpeakerName$EXTENSION"
        } else {
            "-$username$EXTENSION"
        }
    val maxLabelBytes = MAX_TITLE_BYTES - prefix.utf8Length() - suffix.utf8Length()
    val sanitizedLabel = truncateToUtf8Bytes(sanitizeTitleLabel(label), maxLabelBytes.coerceAtLeast(0))
    return "$prefix$sanitizedLabel$suffix"
}

private fun sanitizeTitleLabel(label: String): String =
    label
        .map { if (it in ILLEGAL_TITLE_CHARS || it.isISOControl()) ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex(" {2,}"), " ")

private fun truncateToUtf8Bytes(
    text: String,
    maxBytes: Int,
): String {
    if (text.utf8Length() <= maxBytes) return text
    var end = text.length
    while (end > 0 && text.substring(0, end).utf8Length() > maxBytes) end--
    return text.substring(0, end).trimEnd()
}

private fun String.utf8Length(): Int = toByteArray(Charsets.UTF_8).size
