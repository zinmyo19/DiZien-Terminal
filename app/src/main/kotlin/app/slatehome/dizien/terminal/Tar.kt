package app.slatehome.dizien.terminal

import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream

class TarEntry(val name: String, val type: Char, val size: Long, val mode: Int, val link: String) {
    val isDir get() = type == '5'
    val isSymlink get() = type == '2'
    val isHardlink get() = type == '1'
    val isFile get() = type == '0' || type == '\u0000' || type == '7'
}

/** Minimal tar reader (ustar, GNU long names, pax headers): enough for official Debian images. */
class TarReader(private val inp: InputStream) {
    private val hdr = ByteArray(512)
    private var remaining = 0L
    private var padding = 0

    private fun readFully(b: ByteArray, off: Int, len: Int): Boolean {
        var n = 0
        while (n < len) {
            val r = inp.read(b, off + n, len - n)
            if (r < 0) { if (n == 0) return false; throw EOFException("Unexpected end of archive") }
            n += r
        }
        return true
    }

    private fun skipData() {
        var left = remaining + padding
        val tmp = ByteArray(8192)
        while (left > 0) {
            val r = inp.read(tmp, 0, minOf(left, tmp.size.toLong()).toInt())
            if (r < 0) throw EOFException("Unexpected end of archive")
            left -= r
        }
        remaining = 0; padding = 0
    }

    private fun str(off: Int, len: Int): String {
        var e = off; while (e < off + len && hdr[e].toInt() != 0) e++
        return String(hdr, off, e - off, Charsets.UTF_8)
    }

    private fun num(off: Int, len: Int): Long {
        if (hdr[off].toInt() and 0x80 != 0) { // base-256 for big values
            var v = (hdr[off].toInt() and 0x7f).toLong()
            for (i in 1 until len) v = (v shl 8) or (hdr[off + i].toLong() and 0xff)
            return v
        }
        var v = 0L
        for (i in off until off + len) {
            val c = hdr[i].toInt()
            if (c in '0'.code..'7'.code) v = v * 8 + (c - '0'.code) else if (c == 0 || c == ' '.code) { if (v != 0L) break }
        }
        return v
    }

    private fun readBlob(size: Long): ByteArray {
        require(size in 0..1_000_000) { "Unreasonable tar header size" }
        val b = ByteArray(size.toInt()); var n = 0
        while (n < b.size) { val r = inp.read(b, n, b.size - n); if (r < 0) throw EOFException(); n += r }
        val pad = ((512 - size % 512) % 512).toInt(); if (pad > 0) { val t = ByteArray(pad); readFully(t, 0, pad) }
        return b
    }

    fun next(): TarEntry? {
        skipData()
        var longName: String? = null
        var longLink: String? = null
        var paxPath: String? = null
        var paxLink: String? = null
        var paxSize: Long? = null
        while (true) {
            if (!readFully(hdr, 0, 512)) return null
            if (hdr.all { it.toInt() == 0 }) return null
            val type = hdr[156].toInt().toChar()
            var size = num(124, 12)
            when (type) {
                'L' -> { longName = String(readBlob(size), Charsets.UTF_8).trimEnd('\u0000'); continue }
                'K' -> { longLink = String(readBlob(size), Charsets.UTF_8).trimEnd('\u0000'); continue }
                'x' -> {
                    val txt = String(readBlob(size), Charsets.UTF_8)
                    for (rec in txt.split('\n')) {
                        val sp = rec.indexOf(' '); if (sp < 0) continue
                        val kv = rec.substring(sp + 1); val eq = kv.indexOf('='); if (eq < 0) continue
                        when (kv.substring(0, eq)) {
                            "path" -> paxPath = kv.substring(eq + 1)
                            "linkpath" -> paxLink = kv.substring(eq + 1)
                            "size" -> paxSize = kv.substring(eq + 1).toLongOrNull()
                        }
                    }
                    continue
                }
                'g' -> { readBlob(size); continue }
            }
            paxSize?.let { size = it }
            var name = str(0, 100)
            if (str(257, 5) == "ustar") { val prefix = str(345, 155); if (prefix.isNotEmpty()) name = "$prefix/$name" }
            name = paxPath ?: longName ?: name
            val link = paxLink ?: longLink ?: str(157, 100)
            remaining = if (type == '0' || type == '\u0000' || type == '7') size else 0
            padding = if (remaining > 0) ((512 - remaining % 512) % 512).toInt() else 0
            return TarEntry(name, type, size, num(100, 8).toInt(), link)
        }
    }

    /** Stream over the data of the current entry. */
    val data: InputStream = object : InputStream() {
        override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val r = inp.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (r < 0) throw EOFException("Unexpected end of archive")
            remaining -= r; return r
        }
    }
}

/** Counts bytes read so extraction progress can be shown. */
class CountingInputStream(inp: InputStream) : FilterInputStream(inp) {
    @Volatile var count = 0L
    override fun read(): Int { val r = super.read(); if (r >= 0) count++; return r }
    override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) count += r; return r }
}
