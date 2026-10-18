package com.lerchenflo.willhabenupdater.utilities

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class HtmlTextTest {

    @Test
    fun lineBreakTagsBecomeNewlines() {
        assertThat(HtmlText.toPlainText("Zeile 1<br>Zeile 2<br/>Zeile 3<br />Ende")).isEqualTo("Zeile 1\nZeile 2\nZeile 3\nEnde")
    }

    @Test
    fun otherTagsAreRemovedAndEntitiesDecoded() {
        assertThat(HtmlText.toPlainText("<p>Preis &amp; Versand &lt;fix&gt; &quot;neu&quot; &#39;ok&#39;&nbsp;!</p>")).isEqualTo("Preis & Versand <fix> \"neu\" 'ok' !")
    }

    @Test
    fun outerWhitespaceIsTrimmed() {
        assertThat(HtmlText.toPlainText("  <br>Text<br>  ")).isEqualTo("Text")
    }

    @Test
    fun plainTextBecomesHtmlWithLineBreaks() {
        assertThat(HtmlText.fromPlainText("Rad & Helm <neu>\nZweite Zeile")).isEqualTo("Rad &amp; Helm &lt;neu&gt;<br />Zweite Zeile")
    }
}
