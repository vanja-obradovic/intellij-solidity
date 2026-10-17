package me.serce.solidity.ide.inspections

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.PsiTestUtil
import me.serce.solidity.lang.psi.SolImportDirective
import java.nio.file.Files

class ResolveNameInspectionTest : SolInspectionsTestBase(ResolveNameInspection()) {
  private val accessControl = """
      pragma solidity ^0.8.0;
      abstract contract AccessControl {
          modifier onlyRole(bytes32 role) { _; }
          function _grantRole(bytes32 role, address account) internal {}
      }
  """

  private val roleUser = """
      pragma solidity ^0.8.0;
      import {AccessControl} from "pkg/AccessControl.sol";
      contract C is AccessControl {
          constructor() { _grantRole(0, msg.sender); }
          function f() external onlyRole(0) {}
      }
  """

  fun testConstructorCallThroughImportAlias() {
    myFixture.addFileToProject("Proxy.sol", """
        pragma solidity ^0.8.0;
        contract Proxy { constructor(address impl) {} }
    """)
    checkByText("""
        pragma solidity ^0.8.0;
        import {Proxy as OZProxy} from "./Proxy.sol";
        contract P is OZProxy {
            constructor(address impl) OZProxy(impl) {}
        }
    """)
  }

  fun testInheritedMembersFromExcludedFolder() {
    val dependency = myFixture.addFileToProject("node_modules/pkg/AccessControl.sol", accessControl)
    val nodeModules = dependency.virtualFile.parent.parent
    PsiTestUtil.addExcludedRoot(module, nodeModules)
    try {
      checkByText(roleUser)
    } finally {
      PsiTestUtil.removeExcludedRoot(module, nodeModules)
    }
  }

  // pnpm layout: node_modules/pkg is a link into a store folder, and only the store is indexed
  fun testInheritedMembersThroughSymlinkedPackage() {
    val root = FileUtil.createTempDirectory("sol-pnpm", null).toPath().toRealPath()
    val store = Files.createDirectories(root.resolve("store/pkg"))
    Files.writeString(store.resolve("AccessControl.sol"), accessControl)
    val project = Files.createDirectories(root.resolve("project/node_modules"))
    Files.createSymbolicLink(project.resolve("pkg"), store)
    Files.writeString(root.resolve("project/main.sol"), roleUser)

    VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
    val rootFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)!!
    VfsUtil.markDirtyAndRefresh(false, true, true, rootFile)
    PsiTestUtil.addContentRoot(module, rootFile)
    PsiTestUtil.addExcludedRoot(module, rootFile.findFileByRelativePath("project/node_modules")!!)
    try {
      myFixture.configureFromExistingVirtualFile(rootFile.findFileByRelativePath("project/main.sol")!!)
      enableInspection()
      myFixture.checkHighlighting()

      val import = myFixture.file.childrenOfType<SolImportDirective>().single()
      val target = import.importPath!!.reference!!.resolve()!!.containingFile.virtualFile
      assertEquals(store.resolve("AccessControl.sol").toString(), target.path)
    } finally {
      PsiTestUtil.removeContentEntry(module, rootFile)
    }
  }
}
