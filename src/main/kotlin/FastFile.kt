package pingu

import io.ktor.http.*
import kotlinx.html.Entities
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

data class FastFileEntry(val name: String, val offset: Int, val size: Int, val key: Int, val crc: Int)

class FastFile(basePath: String) {
    private val entries = ConcurrentHashMap<String, FastFileEntry>()
    private val iddPath = Path("$basePath.idd")
    private val idxPath = Path("$basePath.idx")

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (!SystemFileSystem.exists(idxPath)) return

        val source = Buffer().apply {
            SystemFileSystem.source(idxPath).buffered().use { it.transferTo(this) }
        }

        // 解密
        val fileKey = source.peek().readIntLe() // 試了好幾版client都是0 因為算法的關係可以直接當key用
        val isNewFormat = fileKey and 0x7FFFFFFF > 0x1000000

        if (isNewFormat)
            source.decryptInPlace(fileKey)

        // 開始讀取
        val count = if (isNewFormat) {
            val fileKey = source.readIntLe() // 試了好幾版client都是0 因為算法的關係可以直接當key用
            val unk1 = source.readIntLe() // 15
            source.readIntLe()
        } else {
            source.readIntLe()
        }

        repeat(count) {
            val nameLen = source.readIntLe()
            val name = source.readString(nameLen, Charset.forName("MS949"))
            val offset = source.readIntLe()
            val size = source.readIntLe()

            val (key, crc) = if (isNewFormat) {
                source.readIntLe() to source.readIntLe()
            } else {
                0 to 0
            }

            entries[name.lowercase()] = FastFileEntry(name, offset, size, key, crc)
        }
    }

    fun readFile(name: String): ByteArray? {
        val entry = entries[name.lowercase()] ?: return null

        return SystemFileSystem.source(iddPath).buffered().use { source ->
            source.skip(entry.offset.toLong()) // FastFileSeek
            source.readByteArray(entry.size)   // FastFileRead
        }
    }

    fun getAllEntries() = entries.values.sortedBy { Entities.it.name }
}

fun Buffer.decryptInPlace(key: Int) {
    val block = (size / 4).toInt()
    var lastV = key

    repeat(block) {
        lastV = lastV xor readIntLe()
        writeIntLe(lastV)
    }

    val tail = (size % 4).toInt()
    repeat(tail) { i ->
        val mask = (lastV shr (i * 8)) and 0xFF
        val decByte = (readByte().toInt() and 0xFF) xor mask
        writeByte(decByte.toByte())
    }
}

fun Source.readString(byteCount: Int, charset: Charset): String {
    return readByteArray(byteCount).toString(charset)
}

fun detectContentType(data: ByteArray, pathName: String): ContentType {
    if (data.size < 4) return ContentType.Application.OctetStream

    val b0 = data[0].toInt() and 0xFF
    val b1 = data[1].toInt() and 0xFF
    val b2 = data[2].toInt() and 0xFF

    return when {
        b0 == 0xFF && b1 == 0xD8 -> ContentType.Image.JPEG
        (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) || (b0 == 0xFF && (b1 == 0xFB || b1 == 0xF3)) -> ContentType.Audio.MPEG
        b0 == 0x4D && b1 == 0x54 && b2 == 0x68 -> ContentType.parse("audio/midi")

        // MagicNumber如果對不上 就用路徑判斷
        else -> when {
            pathName.startsWith("MP3/", ignoreCase = true) -> ContentType.Audio.MPEG
            pathName.startsWith("Midi/", ignoreCase = true) -> ContentType.parse("audio/midi")
            pathName.startsWith("Wave/", ignoreCase = true) -> ContentType.parse("audio/wav")
            else -> ContentType.Text.Any
//            else -> ContentType.Application.OctetStream
        }
    }
}

fun ContentType.fileExtension(): String = when (this) {
    ContentType.Image.JPEG -> ".jpg"
    ContentType.Audio.MPEG -> ".mp3"
    ContentType.parse("audio/midi") -> ".mid"
    ContentType.parse("audio/wav") -> ".wav"
    else -> ""
}