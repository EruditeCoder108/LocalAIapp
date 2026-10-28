package dev.bonsai.pocket

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    @Test fun textOnlyModelCannotResolveAProjectorDownload() {
        assertFalse(Models.LFM3.supportsVision)
        assertThrows(IllegalArgumentException::class.java) {Models.LFM3.downloadUrl(Models.LFM3.officialVisionFilename)}
        assertThrows(IllegalArgumentException::class.java) {Models.LFM3.downloadUrl("../../other.gguf")}
    }
    @Test fun pinnedDownloadsUseTheirOwnPublishersAndExistingModelIdsStillResolve() {
        assertTrue(Models.LFM3.downloadUrl(Models.LFM3.officialFilename).startsWith("https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/"))
        assertTrue(Models.TWO.downloadUrl(Models.TWO.officialVisionFilename).contains("prism-ml/Ternary-Bonsai-2-2B-gguf/resolve/683f7be"))
        assertTrue(Models.HERETIC_TWO.downloadUrl(Models.HERETIC_TWO.officialFilename).contains("darrellbest/Qwen3.5-2B-Heretic-GGUF"))
        assertSame(Models.TWO,Models.find("2b"));assertSame(Models.FOUR,Models.find("4b"))
        assertSame(Models.HERETIC_TWO,Models.find("heretic-2b"));assertSame(Models.LFM3,Models.find("lfm3-1.7b"))
        assertEquals(Models.all.size,Models.all.map {it.filename}.toSet().size)
    }
}
