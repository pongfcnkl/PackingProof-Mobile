package app.packingproof.mobile

import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPathPreparationTest {
    @Test
    fun `录像父目录不存在时自动递归创建`() {
        val root = Files.createTempDirectory("packingproof-recording-path").toFile()
        try {
            val parent = RecordingPathPreparation.ensureParent(
                root.resolve("recordings/.pending/session.mp4").path,
            )

            assertTrue(parent.isDirectory)
            assertTrue(root.resolve("recordings/.pending").isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
