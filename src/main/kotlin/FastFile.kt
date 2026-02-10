import io.ktor.http.ContentType
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

data class FastFileEntry(val name: String, val offset: Int, val size: Int)

class FastFile(basePath: String) {
    private val entries = ConcurrentHashMap<String, FastFileEntry>()
    private val iddPath = Path("$basePath.idd")
    private val idxPath = Path("$basePath.idx")

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (!SystemFileSystem.exists(idxPath)) return

        SystemFileSystem.source(idxPath).buffered().use { source ->
            val count = source.readIntLe()
            repeat(count) {
                val nameLen = source.readIntLe()
                val name = source.readString(nameLen, Charset.forName("MS949"))
                val offset = source.readIntLe()
                val size = source.readIntLe()
                entries[name.lowercase()] = FastFileEntry(name, offset, size)
            }
        }
    }

    fun readFile(name: String): ByteArray? {
        val entry = entries[name.lowercase()] ?: return null

        return SystemFileSystem.source(iddPath).buffered().use { source ->
            source.skip(entry.offset.toLong()) // FastFileSeek
            source.readByteArray(entry.size)   // FastFileRead
        }
    }

    fun getAllEntries() = entries.values.sortedBy { it.name }
}

fun Source.readString(byteCount: Int, charset: Charset): String {
    return readByteArray(byteCount).toString(charset)
}

fun detectContentType(data: ByteArray, pathName: String): ContentType {
    if (data.size < 4) return ContentType.Application.OctetStream

    return when {
        // JPEG: FF D8
        data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> ContentType.Image.JPEG
        // MP3: ID3 (49 44 33) 或 FF FB
        (data[0] == 0x49.toByte() && data[1] == 0x44.toByte() && data[2] == 0x33.toByte()) ||
                (data[0] == 0xFF.toByte() && (data[1] == 0xFB.toByte() || data[1] == 0xF3.toByte())) -> ContentType.Audio.MPEG
        // MIDI: 4D 54 68 64 (MThd)
        data[0] == 0x4D.toByte() && data[1] == 0x54.toByte() && data[2] == 0x68.toByte() -> ContentType.parse("audio/midi")

        // MagicNumber如果對不上 就用路徑判斷
        else -> when {
            pathName.startsWith("MP3/", ignoreCase = true) -> ContentType.Audio.MPEG
            pathName.startsWith("Midi/", ignoreCase = true) -> ContentType.parse("audio/midi")
            else -> ContentType.Application.OctetStream
        }
    }
}

fun ContentType.fileExtension(): String = when (this) {
    ContentType.Image.JPEG -> ".jpg"
    ContentType.Audio.MPEG -> ".mp3"
    ContentType.parse("audio/midi") -> ".mid"
    else -> ""
}