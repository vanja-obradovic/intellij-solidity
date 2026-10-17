package me.serce.solidity.lang.core.resolve

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import me.serce.solidity.lang.psi.SolImportAliasedPair
import me.serce.solidity.lang.psi.SolNamedElement
import me.serce.solidity.lang.psi.SolVarLiteral
import me.serce.solidity.lang.resolve.ref.SolImportConfigService
import me.serce.solidity.lang.resolve.ref.SolImportPathReference

class SolImportResolveTest : SolResolveTestBase() {
  fun testImportPathResolve() = testResolveToAnotherFile(
    InlineFile(
      code = "contract a {}",
      name = "Ownable.sol"
    ).psiFile,
    InlineFile(
      """
          import "./Ownable.sol";
                      //^

          contract b {}
    """
    ).psiFile
  )

  fun testImportPathResolveNpm() = testResolveToAnotherFile(
    myFixture.configureByFile("node_modules/util/contracts/TestImport.sol"),
    myFixture.configureByFile("contracts/ImportUsage.sol")
  )

  fun testImportPathResolveEthPM() = testResolveToAnotherFile(
    myFixture.configureByFile("installed_contracts/util/contracts/TestImport.sol"),
    myFixture.configureByFile("contracts/ImportUsageEthPM.sol")
  )


  fun testImportPathResolveFoundry() = testResolveToAnotherFile(
    myFixture.configureByFile("lib/util/src/TestImport.sol"),
    myFixture.configureByFile("contracts/ImportUsageFoundry.sol")
  )


  fun testResolveNameClash() {

    myFixture.configureByFile("contracts/a/SimpleName.sol")
    myFixture.configureByFile("contracts/b/SimpleName.sol")

    myFixture.configureByFile("contracts/ImportNameClash.sol")
    val (refElement) = findElementAndDataInEditor<SolNamedElement>("^")

    val resolved = checkNotNull(refElement.reference?.resolve() as? PsiNamedElement) {
      "Failed to resolve ${refElement.text}"
    }

    assertEquals("abc", resolved.name)
  }

  fun testResolveFunctionImportClash() {
    myFixture.configureByFile("contracts/a/lib.sol")
    myFixture.configureByFile("contracts/b/b.sol")
    myFixture.configureByFile("contracts/b/lib.sol")

    myFixture.configureByFile("contracts/ImportLibNameClash.sol")
    val (refElement) = findElementAndDataInEditor<SolNamedElement>("^")

    val resolved = checkNotNull(refElement.reference?.resolve() as? PsiNamedElement) {
      "Failed to resolve ${refElement.text}"
    }

    assertEquals("lib", resolved.name)
  }

  fun testRecursiveImport() {
    myFixture.configureByFile("recursive/B.sol")
    myFixture.configureByFile("recursive/C.sol")
    myFixture.configureByFile("recursive/dir/C.sol")
    myFixture.configureByFile("recursive/A.sol")
    val (refElement) = findElementAndDataInEditor<SolNamedElement>("^")

    val resolved = checkNotNull(refElement.reference?.resolve() as? PsiNamedElement) {
      "Failed to resolve ${refElement.text}"
    }

    assertEquals("C", resolved.name)
  }

  fun testCircularImport() {
    myFixture.configureByFile("circular/IMain.sol")
    myFixture.configureByFile("circular/IRandomLib.sol")
    myFixture.configureByFile("circular/Main.sol")
    val (refElement) = findElementAndDataInEditor<SolNamedElement>("^")

    val resolved = checkNotNull(refElement.reference?.resolve() as? PsiNamedElement) {
      "Failed to resolve ${refElement.text}"
    }

    assertEquals("RandomStruct", resolved.name)
  }

  fun testImportStructAndConstant() {
    InlineFile(
      """
      struct Struct1 {
          string foo;
      }

      string constant STR_CONST = "1";
      
      type UserDefinedType is string;
    """.trimIndent(), "Utils.sol"
    )
    InlineFile(
      """
        pragma solidity ^0.8.0;

        import {Struct1, STR_CONST, UserDefinedType} from "./Utils.sol";

        contract C1 {
            Struct1 public s1;
            //^
            function test() public pure returns (UserDefinedType memory) {
                                                    //^
                return STR_CONST;
                       //^
            }
        }
      """.trimIndent()
    )

    val (structElement, userDefinedTypeElement, constElement) = findMultipleElementAndDataInEditor<SolNamedElement>("^")
    val structRef = structElement.first
    val structResolved = checkNotNull(structRef.reference?.resolve() as? PsiNamedElement) {
      "Failed to resolve ${structRef.text}"
    }
    assertEquals("Struct1", structResolved.name)

    val userDefinedTypeRef = userDefinedTypeElement.first
    val userDefinedTypeResolved = checkNotNull(userDefinedTypeRef.reference?.resolve() as? PsiNamedElement) {
      "Failed to resolve ${userDefinedTypeRef.text}"
    }
    assertEquals("UserDefinedType", userDefinedTypeResolved.name)

    val constRef = constElement.first
    val constResolved = checkNotNull(constRef.reference?.resolve() as? PsiNamedElement) {
      "Failed to resolve ${constRef.text}"
    }
    assertEquals("STR_CONST", constResolved.name)
  }

  fun testImportPathResolveAfterTargetFileDeleted() {
    val target = myFixture.addFileToProject("contracts/Ownable.sol", "contract Ownable {}")
    val usage = myFixture.addFileToProject(
      "contracts/Usage.sol",
      """
          import "./Ownable.sol";
                  //^
          contract Usage {}
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(usage.virtualFile)

    val (beforeRef) = findElementAndDataInEditor<SolNamedElement>("^")
    assertNotNull(beforeRef.reference?.resolve())

    WriteCommandAction.runWriteCommandAction(project) {
      target.virtualFile.delete(this)
    }

    val (afterRef) = findElementAndDataInEditor<SolNamedElement>("^")
    assertNull(afterRef.reference?.resolve())
  }

  fun testImportPathResolveWithInvalidVirtualFile() {
    val file = myFixture.addFileToProject("contracts/Ownable.sol", "contract Ownable {}").virtualFile
    WriteCommandAction.runWriteCommandAction(project) {
      file.delete(this)
    }

    assertFalse(file.isValid)
    assertNull(SolImportPathReference.findImportFile(project, file, "./Ownable.sol"))
  }

  fun testImportPathResolveWithInvalidContainingFile() {
    val usage = myFixture.addFileToProject(
      "contracts/Usage.sol",
      """
          import "./Ownable.sol";
                  //^
          contract Usage {}
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(usage.virtualFile)
    val (refElement) = findElementAndDataInEditor<SolNamedElement>("^")
    val reference = refElement.reference as SolImportPathReference

    WriteCommandAction.runWriteCommandAction(project) {
      usage.virtualFile.delete(this)
    }

    assertFalse(usage.virtualFile.isValid)
    assertNull(reference.singleResolve())
  }

  fun testImportConfigResolveWithInvalidFile() {
    val file = myFixture.addFileToProject("contracts/Ownable.sol", "contract Ownable {}").virtualFile
    WriteCommandAction.runWriteCommandAction(project) {
      file.delete(this)
    }

    assertFalse(file.isValid)
    assertNull(SolImportConfigService.getInstance(project).resolve("Ownable.sol", file))
  }

  fun testImportConfigReverseRemappingsWithInvalidFile() {
    val file = myFixture.addFileToProject("contracts/Ownable.sol", "contract Ownable {}").virtualFile
    WriteCommandAction.runWriteCommandAction(project) {
      file.delete(this)
    }

    assertFalse(file.isValid)
    assertTrue(SolImportConfigService.getInstance(project).reverseRemappings(file).isEmpty())
  }

  // Two files declare the same library; the importing file names one of them directly and reaches the other
  // through another import. Repeated under ten names, because a wrong pick used to depend on hash order.
  fun testDirectImportWinsOverTransitiveOne() = repeat(10) { i ->
    myFixture.addFileToProject("v1/T$i.sol", "library L$i { function f() internal pure {} }")
    myFixture.addFileToProject("v2/T$i.sol", "library L$i { function f() internal pure {} }")
    myFixture.addFileToProject("Mid$i.sol", """
        import {L$i} from "./v1/T$i.sol";
        contract M$i {}
    """)
    val main = myFixture.addFileToProject("Main$i.sol", """
        import {M$i} from "./Mid$i.sol";
        import {L$i} from "./v2/T$i.sol";
        contract C$i is M$i { function g() internal pure { L$i.f(); } }
    """)
    assertEquals("v2/T$i.sol", resolvedFilePath(main, "L$i"))
  }

  // `import {L as LV1}` makes only LV1 visible, so a plain L means the other import
  fun testAliasHidesTheOriginalName() = repeat(10) { i ->
    myFixture.addFileToProject("v1/A$i.sol", "library P$i { function f() internal pure {} }")
    myFixture.addFileToProject("v2/A$i.sol", "library P$i { function f() internal pure {} }")
    val main = myFixture.addFileToProject("AliasMain$i.sol", """
        import {P$i} from "./v2/A$i.sol";
        import {P$i as PV1$i} from "./v1/A$i.sol";
        contract C$i { function g() internal pure { P$i.f(); PV1$i.f(); } }
    """)
    assertEquals("v2/A$i.sol", resolvedFilePath(main, "P$i"))
    assertEquals("v1/A$i.sol", resolvedFilePath(main, "PV1$i"))
  }

  // the imported name in each `import {V as X}` belongs to that import's own file
  fun testImportedNameResolvesInItsImportFile() {
    myFixture.addFileToProject("a/V.sol", "contract V {}")
    myFixture.addFileToProject("b/V.sol", "contract V {}")
    val main = myFixture.addFileToProject("ImportedName.sol", """
        import {V as VA} from "./a/V.sol";
        import {V as VB} from "./b/V.sol";
        contract C {}
    """)
    val targets = PsiTreeUtil.findChildrenOfType(main, SolImportAliasedPair::class.java)
      .map { it.userDefinedTypeName.reference!!.resolve()!!.containingFile.virtualFile.path.substringAfter("/src/") }
    assertEquals(listOf("a/V.sol", "b/V.sol"), targets)
  }

  private fun resolvedFilePath(file: PsiFile, name: String): String {
    val literal = PsiTreeUtil.findChildrenOfType(file, SolVarLiteral::class.java)
      .first { it.name == name }
    val target = checkNotNull(literal.reference?.resolve()) { "$name is unresolved" }
    return target.containingFile.virtualFile.path.substringAfter("/src/")
  }

  override fun getTestDataPath() = "src/test/resources/fixtures/import/"
}
