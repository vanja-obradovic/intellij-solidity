package me.serce.solidity.ide.usage

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import me.serce.solidity.lang.psi.*

class SolUseScopeTest : SolUsageTestBase() {
  private val code = """
      pragma solidity ^0.8.0;
      contract A {
          struct S { uint amount; }
          uint private secret;
          function f(uint amount) internal pure returns (uint) {
              uint doubled = amount + amount;
              return doubled;
          }
          function g(uint amount) private pure returns (uint) { return amount; }
      }
  """

  private inline fun <reified T : PsiElement> named(name: String): T =
    PsiTreeUtil.findChildrenOfType(myFixture.file, T::class.java).first { (it as SolNamedElement).name == name }

  fun testParameterIsSearchedInItsFunction() {
    InlineFile(code)
    val param = PsiTreeUtil.findChildrenOfType(myFixture.file, SolParameterDef::class.java).first { it.name == "amount" }
    assertEquals(listOf(named<SolFunctionDefinition>("f")), (param.useScope as LocalSearchScope).scope.toList())
    assertEquals(2, myFixture.findUsages(param).size)
  }

  fun testLocalVariableIsSearchedInItsFunction() {
    InlineFile(code)
    val local = named<SolVariableDeclaration>("doubled")
    assertEquals(listOf(named<SolFunctionDefinition>("f")), (local.useScope as LocalSearchScope).scope.toList())
    assertEquals(1, myFixture.findUsages(local).size)
  }

  fun testStructMemberIsSearchedEverywhere() {
    InlineFile(code)
    val member = PsiTreeUtil.findChildrenOfType(myFixture.file, SolVariableDeclaration::class.java)
      .first { it.parent is SolStructDefinition }
    assertTrue(member.useScope is GlobalSearchScope)
  }

  fun testPrivateMembersAreSearchedInTheirContract() {
    InlineFile(code)
    val contract = listOf(named<SolContractDefinition>("A"))
    assertEquals(contract, (named<SolFunctionDefinition>("g").useScope as LocalSearchScope).scope.toList())
    assertEquals(contract, (named<SolStateVariableDeclaration>("secret").useScope as LocalSearchScope).scope.toList())
  }

  fun testOtherDeclarationsAreSearchedInSolidityFiles() {
    val js = myFixture.addFileToProject("app.js", "const f = 1;")
    val other = myFixture.addFileToProject("B.sol", """
        pragma solidity ^0.8.0;
        import "./main.sol";
        contract B is A {
            function h() internal pure returns (uint) { return f(1); }
        }
    """)
    myFixture.configureByText("main.sol", code)
    val f = named<SolFunctionDefinition>("f")
    val scope = f.useScope as GlobalSearchScope
    assertTrue(scope.contains(other.virtualFile))
    assertFalse(scope.contains(js.virtualFile))
    assertEquals(1, myFixture.findUsages(f).size)
  }
}
