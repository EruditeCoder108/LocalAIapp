package dev.bonsai.pocket

import org.junit.Assert.*
import org.junit.Test

class CodeArtifactsTest {
    @Test fun namedFilesKeepTheExactSourceAndLeaveExplanationsOutside() {
        val parts=CodeArtifacts.parse("Here is your button.\n```html filename=button.html\n<button>Hi</button>\n```\nDone.")
        assertEquals(3,parts.size)
        val file=(parts[1] as ResponsePart.File).artifact
        assertEquals("button.html",file.name);assertEquals("<button>Hi</button>\n",file.source)
        assertTrue(file.complete);assertTrue(file.previewable);assertEquals("text/html",file.mime)
    }
    @Test fun streamingAndNestedFencesNeverTurnCodeIntoProse() {
        val partial=CodeArtifacts.parse("```python filename=hello.py\nprint('hello')")
        assertEquals(1,partial.size);assertFalse((partial.single() as ResponsePart.File).artifact.complete)
        val nested=CodeArtifacts.parse("````md filename=readme.md\n```js\nalert(1)\n```\n````")
        assertEquals(1,nested.size);assertTrue((nested.single() as ResponsePart.File).artifact.source.contains("```js"))
    }
    @Test fun oldUnlabelledBlocksAndRawSvgBecomeUsableNamedFiles() {
        val files=CodeArtifacts.parse("```html\n<h1>Hello</h1>\n```\n```css\nbody{color:red}\n```\n```html\n<h2>Two</h2>\n```").filterIsInstance<ResponsePart.File>()
        assertEquals(listOf("index.html","styles.css","index-2.html"),files.map {it.artifact.name})
        assertEquals("drawing.svg",(CodeArtifacts.parse("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>").single() as ResponsePart.File).artifact.name)
        assertEquals("evil.html",CodeArtifacts.safeName("../../evil.html"))
        assertEquals("button.html",(CodeArtifacts.parse("```html filename=\"../../button.html\"\n<button/>\n```").single() as ResponsePart.File).artifact.name)
    }
    @Test fun previewUsesAnOpaqueSandboxAndBundlesOnlyNamedCompanionFiles() {
        val html=CodeArtifact("index.html","<link href='styles.css' rel='stylesheet'><script src='app.js'></script><img src='https://example.com/x'>","html")
        val document=CanvasDocument.html(html,listOf(CodeArtifact("styles.css","body{color:red}","css"),CodeArtifact("app.js","document.body.dataset.ready='yes'","js")))
        assertTrue(document.contains("sandbox=\"allow-scripts\""));assertFalse(document.contains("allow-same-origin"))
        assertTrue(document.contains("connect-src 'none'"));assertTrue(document.contains("form-action 'none'"))
        assertTrue(document.contains("data:text/css;base64,"));assertTrue(document.contains("data:text/javascript;base64,"))
        assertTrue("External URL must remain subject to CSP and the network blocker",document.contains("https://example.com/x"))
        assertFalse(document.contains("srcdoc=\"<"))
    }
}
