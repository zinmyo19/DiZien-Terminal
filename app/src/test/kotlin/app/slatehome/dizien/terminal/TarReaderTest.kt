package app.slatehome.dizien.terminal

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

class TarReaderTest {
    private fun header(name: String, type: Char, size: Int, link: String = ""): ByteArray {
        val h = ByteArray(512)
        name.toByteArray().copyInto(h)
        "0000644\u0000".toByteArray().copyInto(h, 100)
        String.format("%011o\u0000", size).toByteArray().copyInto(h, 124)
        h[156] = type.code.toByte()
        link.toByteArray().copyInto(h, 157)
        return h
    }

    @Test fun readsFilesLinksAndDirs() {
        val out = ByteArrayOutputStream()
        out.write(header("d/", '5', 0))
        out.write(header("d/f.txt", '0', 5)); out.write("hello".toByteArray()); out.write(ByteArray(507))
        out.write(header("d/l", '2', 0, "f.txt"))
        out.write(ByteArray(1024))
        val r = TarReader(ByteArrayInputStream(out.toByteArray()))
        val a = r.next()!!; assertEquals("d/", a.name); assert(a.isDir)
        val b = r.next()!!; assertEquals(5L, b.size); assertEquals("hello", String(r.data.readBytes()))
        val c = r.next()!!; assert(c.isSymlink); assertEquals("f.txt", c.link)
        assertEquals(null, r.next())
    }

    /** Set DIZIEN_ROOTFS to a Debian rootfs.tar.gz to run this check against the real image. */
    @Test fun readsRealDebianImage() {
        val f = System.getenv("DIZIEN_ROOTFS")?.let(::File)
        assumeTrue(f != null && f.isFile)
        val r = TarReader(GZIPInputStream(f!!.inputStream().buffered()))
        var files = 0; var dirs = 0; var syms = 0; var hard = 0; var bytes = 0L
        while (true) {
            val e = r.next() ?: break
            when {
                e.isDir -> dirs++; e.isSymlink -> syms++; e.isHardlink -> hard++
                e.isFile -> { files++; bytes += r.data.copyTo(java.io.OutputStream.nullOutputStream()) }
            }
        }
        assertEquals(2677, files); assertEquals(415, dirs); assertEquals(173, syms); assertEquals(1, hard)
        assertEquals(100641616L, bytes)
    }
}
