package com.oyayasu.ankivideoplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleParserTest {
    @Test fun parsesSrt() {
        val data = "1\n00:00:01,000 --> 00:00:02,500\nHello there!\n"
        val cues = SubtitleParser.parse(data)
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals("Hello there!", cues[0].text)
    }
    @Test fun parsesVtt() {
        val data = "WEBVTT\n\n00:01.000 --> 00:02.000\nHi\n"
        assertEquals("Hi", SubtitleParser.parse(data)[0].text)
    }
    @Test fun parsesAss() {
        val data = "[Events]\nDialogue: 0,0:00:01.20,0:00:03.40,Default,,0,0,0,,Hello\\Nworld"
        val cues = SubtitleParser.parse(data)
        assertEquals(1200L, cues[0].startMs)
        assertEquals("Hello world", cues[0].text)
    }
}
