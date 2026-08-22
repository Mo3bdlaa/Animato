package aniyomi.core.common.torrent.bencode

import java.io.EOFException
import java.io.InputStream
import java.util.TreeMap

class BencodeParser private constructor(val input: InputStream) {
    /**
     * How deep the containers currently nest.
     *
     * `llllll…` recursed until the stack ran out, and StackOverflowError is not something the
     * caller was catching. A torrent file is a dictionary of dictionaries and one list of paths;
     * anything approaching the cap is not a description of a torrent.
     */
    private var depth = 0

    companion object {
        fun parse(input: InputStream): BencodeValue {
            val parser = BencodeParser(input)
            val result = parser.parseValue(parser.readNextByte())
            require(input.read() == -1) { "Unexpected extra data" }
            return result
        }

        private const val BYTE_CHAR_LOWERCASE_I: Byte = 'i'.code.toByte()
        private const val BYTE_CHAR_LOWERCASE_L: Byte = 'l'.code.toByte()
        private const val BYTE_CHAR_LOWERCASE_D: Byte = 'd'.code.toByte()
        private const val BYTE_CHAR_LOWERCASE_E: Byte = 'e'.code.toByte()

        /** A torrent file is metadata; nothing legitimate inside one is a hundred megabytes. */
        private const val MAX_STRING_LENGTH = 100L * 1024 * 1024

        /** Comfortably past the widest real integer here (a file size) and short of overflowing. */
        private const val MAX_NUMBER_DIGITS = 19

        /** A dictionary of dictionaries with one list of path components. Five is generous. */
        private const val MAX_DEPTH = 32

        private const val BYTE_CHAR_COLON: Byte = ':'.code.toByte()
        private const val BYTE_CHAR_HYPHEN: Byte = '-'.code.toByte()
        private const val BYTE_CHAR_0: Byte = '0'.code.toByte()
        private const val BYTE_CHAR_1: Byte = '1'.code.toByte()
        private const val BYTE_CHAR_9: Byte = '9'.code.toByte()
    }

    private fun parseValue(head: Byte): BencodeValue {
        return when (head) {
            BYTE_CHAR_LOWERCASE_I -> parseInteger()
            in BYTE_CHAR_0..BYTE_CHAR_9 -> parseByteString(head)
            BYTE_CHAR_LOWERCASE_L -> parseList()
            BYTE_CHAR_LOWERCASE_D -> parseDictionary()
            else -> throw IllegalArgumentException("Unexpected value type")
        }
    }

    private fun parseInteger(): BencodeValue.Integer {
        val result = parseNumberHelper(readNextByte(), BYTE_CHAR_LOWERCASE_E)
        return BencodeValue.Integer(result)
    }

    private fun parseByteString(head: Byte): BencodeValue.ByteString {
        val length = parseStringLength(head)
        val result = ByteArray(length)
        for (i in 0..<length) {
            result[i] = readNextByte()
        }
        return BencodeValue.ByteString(result)
    }

    /**
     * A declared length, bounded by what a torrent file plausibly contains.
     *
     * The `require` only ever rejected negatives, and nothing compared the declared length against
     * the bytes actually available — so `2147483647:` at the end of a truncated file allocated two
     * gigabytes and then hit EOF, and an OutOfMemoryError is an Error that nothing on this path
     * catches. A cap is the honest guard: no legitimate string inside a `.torrent` is anywhere
     * near it, and a file claiming otherwise is one to refuse rather than to try.
     */
    private fun parseStringLength(head: Byte): Int {
        val result = parseNumberHelper(head, BYTE_CHAR_COLON)
        require(result in 0..MAX_STRING_LENGTH) { "Invalid string length" }
        return result.toInt()
    }

    private fun parseList(): BencodeValue.List {
        depth++
        require(depth <= MAX_DEPTH) { "Nesting too deep" }
        val result = ArrayList<BencodeValue>()
        while (true) {
            val b = readNextByte()
            if (b == BYTE_CHAR_LOWERCASE_E) {
                break
            }
            result.add(parseValue(b))
        }
        depth--
        return BencodeValue.List(result)
    }

    private fun parseDictionary(): BencodeValue.Dictionary {
        depth++
        require(depth <= MAX_DEPTH) { "Nesting too deep" }
        val result = TreeMap<BencodeValue.ByteString, BencodeValue>()
        while (true) {
            val b = readNextByte()
            if (b == BYTE_CHAR_LOWERCASE_E) {
                break
            }
            val key = parseByteString(b)
            require(result.isEmpty() || result.lastKey() < key) { "Dictionary keys out of order" }
            result[key] = parseValue(readNextByte())
        }
        depth--
        return BencodeValue.Dictionary(result)
    }

    private fun parseNumberHelper(head: Byte, terminatingCharacter: Byte): Long {
        val sb = StringBuilder()

        var b = head
        do {
            val ok: Boolean = when {
                // First character can be minus sign or digit
                sb.isEmpty() -> b == BYTE_CHAR_HYPHEN || (b in BYTE_CHAR_0..BYTE_CHAR_9)
                // Can't have any other characters if the number is zero
                sb.contentEquals("0") -> false
                // Can't have minus zero, so after hyphen only 1-9 are valid
                sb.contentEquals("-") -> (b in BYTE_CHAR_1..BYTE_CHAR_9)
                // Remaining characters must be digits
                else -> (b in BYTE_CHAR_0..BYTE_CHAR_9)
            }
            require(ok) { "Unexpected integer character" }
            // Every character was checked and the count never was, so a long enough run of digits
            // reached `toLong()` as a NumberFormatException instead of as a rejected file.
            require(sb.length < MAX_NUMBER_DIGITS) { "Integer too long" }
            sb.append(Char(b.toUShort()))
        } while ((readNextByte().also { b = it }) != terminatingCharacter)

        return sb.toString().toLong()
    }

    private fun readNextByte(): Byte {
        val result = input.read()
        if (result == -1) {
            throw EOFException()
        }
        return result.toByte()
    }
}
