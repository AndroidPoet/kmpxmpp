package io.github.androidpoet.kmpxmpp.core

public data class XmlStartTag(
    val name: String,
    val attributes: Map<String, String>,
    val start: Int,
    val endExclusive: Int,
)

public object XmppXmlMiniParser {
    public fun parseStartTags(xml: String): List<XmlStartTag> {
        val tags = mutableListOf<XmlStartTag>()
        var index = 0
        while (true) {
            val open = xml.indexOf('<', index)
            if (open == -1 || open + 1 >= xml.length) break
            val next = xml[open + 1]
            if (next == '/' || next == '?' || next == '!') {
                index = open + 1
                continue
            }
            val close = xml.indexOf('>', open + 1)
            if (close == -1) break
            val raw = xml.substring(open + 1, close).trim()
            if (raw.isEmpty()) {
                index = close + 1
                continue
            }
            val nameToken = raw.substringBefore(' ').removeSuffix("/").trim()
            val localName = nameToken.substringAfter(':').lowercase()
            val attributesRaw = raw.removePrefix(nameToken).removeSuffix("/").trim()
            tags += XmlStartTag(
                name = localName,
                attributes = parseAttributes(attributesRaw),
                start = open,
                endExclusive = close + 1,
            )
            index = close + 1
        }
        return tags
    }

    public fun tagInnerXml(xml: String, tag: XmlStartTag): String? {
        val closeStart = findClosingTag(xml, tag.name, tag.endExclusive) ?: return null
        return xml.substring(tag.endExclusive, closeStart)
    }

    public fun textForTag(xml: String, localName: String): String? {
        val startTag = parseStartTags(xml).firstOrNull { it.name == localName } ?: return null
        val closingStart = findClosingTag(xml, localName, startTag.endExclusive) ?: return null
        return xml.substring(startTag.endExclusive, closingStart)
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val attributes = linkedMapOf<String, String>()
        var i = 0
        while (i < raw.length) {
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i >= raw.length) break
            val keyStart = i
            while (i < raw.length && raw[i] != '=' && !raw[i].isWhitespace()) i++
            if (i <= keyStart) break
            val key = normalizeAttributeKey(raw.substring(keyStart, i))
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i >= raw.length || raw[i] != '=') {
                attributes[key] = ""
                continue
            }
            i++
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i >= raw.length) {
                attributes[key] = ""
                break
            }
            val quote = raw[i]
            if (quote != '\'' && quote != '"') {
                val valueStart = i
                while (i < raw.length && !raw[i].isWhitespace()) i++
                attributes[key] = raw.substring(valueStart, i)
                continue
            }
            i++
            val valueStart = i
            while (i < raw.length && raw[i] != quote) i++
            attributes[key] = raw.substring(valueStart, i)
            if (i < raw.length) i++
        }
        return attributes
    }

    private fun normalizeAttributeKey(raw: String): String {
        val lower = raw.lowercase()
        return if (lower == "xmlns" || lower.startsWith("xmlns:")) "xmlns" else lower.substringAfter(':')
    }

    private fun findClosingTag(xml: String, localName: String, fromIndex: Int): Int? {
        var depth = 0
        var index = fromIndex
        while (index < xml.length) {
            val open = xml.indexOf('<', index)
            if (open == -1 || open + 1 >= xml.length) return null
            val next = xml[open + 1]
            if (next == '!' || next == '?') {
                val close = xml.indexOf('>', open + 1)
                if (close == -1) return null
                index = close + 1
                continue
            }
            val close = xml.indexOf('>', open + 1)
            if (close == -1) return null
            val raw = xml.substring(open + 1, close).trim()
            if (raw.isEmpty()) {
                index = close + 1
                continue
            }
            val isClosing = raw.startsWith("/")
            val token = if (isClosing) raw.removePrefix("/").trim() else raw
            val tokenName = token.substringBefore(' ').removeSuffix("/").substringAfter(':').lowercase()
            val selfClosing = !isClosing && raw.endsWith("/")
            if (tokenName == localName) {
                if (isClosing) {
                    if (depth == 0) return open
                    depth--
                } else if (!selfClosing) {
                    depth++
                }
            }
            index = close + 1
        }
        return null
    }
}
