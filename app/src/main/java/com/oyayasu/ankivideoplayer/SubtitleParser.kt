package com.oyayasu.ankivideoplayer

data class CueLine(val startMs: Long, val endMs: Long, val text: String)

object SubtitleParser {
    private val stamp = Regex("""(?:(\d+):)?(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val tags = Regex("""<[^>]+>|\{\\[^}]*}""")

    fun parse(input: String): List<CueLine> {
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        if (normalized.contains("[Events]") && normalized.contains("Dialogue:")) {
            return parseAss(normalized)
        }
        val blocks = normalized.split(Regex("""\n\s*\n"""))
        return blocks.mapNotNull { block ->
            val lines = block.lines()
            val index = lines.indexOfFirst { it.contains("-->") }
            if (index < 0) return@mapNotNull null
            val halves = lines[index].split("-->", limit = 2)
            val start = parseTime(halves[0]) ?: return@mapNotNull null
            val end = parseTime(halves[1]) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            val text = clean(lines.drop(index + 1).joinToString(" "))
            if (text.isBlank()) null else CueLine(start, end, text)
        }.sortedBy { it.startMs }
    }

    private fun parseAss(source: String): List<CueLine> =
        source.lineSequence().filter { it.trimStart().startsWith("Dialogue:") }.mapNotNull { raw ->
            val items = raw.substringAfter("Dialogue:").trim().split(",", limit = 10)
            if (items.size < 10) return@mapNotNull null
            val start = parseTime(items[1]) ?: return@mapNotNull null
            val end = parseTime(items[2]) ?: return@mapNotNull null
            val text = clean(items[9].replace("\\N", " ").replace("\\n", " "))
            if (end <= start || text.isBlank()) null else CueLine(start, end, text)
        }.sortedBy { it.startMs }.toList()

    private fun parseTime(value: String): Long? {
        val groups = stamp.find(value.trim())?.groupValues ?: return null
        val hours = groups[1].toLongOrNull() ?: 0L
        val minutes = groups[2].toLongOrNull() ?: return null
        val seconds = groups[3].toLongOrNull() ?: return null
        val millis = groups[4].padEnd(3, '0').take(3).toLongOrNull() ?: return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }

    private fun clean(text: String): String =
        text.replace(tags, "")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">")
            .trim()
}
