package me.serce.solidity.ide.colors

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font

class SolColorSchemesTest : BasePlatformTestCase() {
  private val bundled = setOf("Dark", "Islands Dark", "Darcula", "Light", "IntelliJ Light", "Default")

  fun testBundledSchemesColorTheNewEntries() {
    // the bundled schemes and the editable copies made of them
    val schemes = bundled.flatMap { listOf(it, "_@user_$it") }.mapNotNull { EditorColorsManager.getInstance().getScheme(it) }
    assertTrue("no bundled schemes found", schemes.isNotEmpty())
    for (scheme in schemes) {
      val text = scheme.getAttributes(HighlighterColors.TEXT).foregroundColor
      val local = scheme.getAttributes(SolColor.LOCAL_VARIABLE.textAttributesKey)
      assertTrue("${scheme.name}: locals look like plain text", local.foregroundColor != null && local.foregroundColor != text)
      for (builtin in listOf(SolColor.GLOBAL, SolColor.BUILTIN_MEMBER, SolColor.GLOBAL_FUNCTION_CALL, SolColor.YUL_BUILTIN)) {
        assertEquals("${scheme.name}: $builtin", Font.ITALIC, scheme.getAttributes(builtin.textAttributesKey).fontType and Font.ITALIC)
      }
      assertEquals(Font.ITALIC, scheme.getAttributes(SolColor.INTERFACE_NAME.textAttributesKey).fontType)
      assertEquals(Font.BOLD, scheme.getAttributes(SolColor.LIBRARY_NAME.textAttributesKey).fontType)
      // the write style is drawn over the state variable color, so it must not set a color of its own
      val write = scheme.getAttributes(SolColor.STATE_VARIABLE_WRITE.textAttributesKey)
      assertNull("${scheme.name}: storage write color", write.foregroundColor)
      assertEquals(EffectType.LINE_UNDERSCORE, write.effectType)
    }
  }
}
