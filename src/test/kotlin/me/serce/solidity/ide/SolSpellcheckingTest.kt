package me.serce.solidity.ide

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.spellchecker.tokenizer.LanguageSpellchecking
import me.serce.solidity.lang.SolidityLanguage
import me.serce.solidity.utils.SolTestBase

class SolSpellcheckingTest : SolTestBase() {
  fun testStrategyIsRegistered() {
    assertTrue(LanguageSpellchecking.INSTANCE.allForLanguage(SolidityLanguage).any { it is SolSpellcheckingStrategy })
  }

  fun testTypoInCommentAndString() {
    myFixture.enableInspections(SpellCheckingInspection::class.java)
    InlineFile("""
      // the wrod is wrong
      contract A {
          string s = "anothr one";
      }
    """)
    val typos = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("Typo") }
    assertTrue(typos.toString(), typos.any { "wrod" in it })
    assertTrue(typos.toString(), typos.any { "anothr" in it })
  }
}
