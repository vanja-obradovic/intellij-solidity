package me.serce.solidity.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import me.serce.solidity.lang.SolidityFileType
import me.serce.solidity.lang.YulFileType
import me.serce.solidity.lang.core.SolidityTokenTypes.IDENTIFIER
import me.serce.solidity.lang.psi.*
import me.serce.solidity.lang.resolve.ref.SolReference
import me.serce.solidity.lang.stubs.SolNamedStub
import me.serce.solidity.lang.types.findContract

abstract class SolElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), SolElement {
  override fun getReference(): SolReference? = null
}

abstract class SolNamedElementImpl(node: ASTNode) : SolElementImpl(node), SolNamedElement, PsiNameIdentifierOwner {
  override fun getNameIdentifier(): PsiElement? = findChildByType(IDENTIFIER)

  override fun getName(): String? {
    return nameIdentifier?.text
  }

  override fun setName(name: String): PsiElement? {
    nameIdentifier?.replace(SolPsiFactory(project).createIdentifier(name))
    return this
  }

  override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

  override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

  override fun getUseScope(): SearchScope = solUseScope(super.getUseScope())
}

abstract class SolStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, SolElement {

  constructor(node: ASTNode) : super(node)

  constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getReference(): SolReference? = null

  // FQN isn't needed in paring tests
  override fun toString(): String = "${javaClass.simpleName}($elementType)"
}

abstract class SolStubbedNamedElementImpl<S> :
  SolStubbedElementImpl<S>,
  SolNamedElement,
  PsiNameIdentifierOwner where S : SolNamedStub, S : StubElement<*> {

  constructor(node: ASTNode) : super(node)

  constructor(stub: S, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getNameIdentifier(): PsiElement? = findChildByType(IDENTIFIER)

  override fun getName() = stub?.name ?: nameIdentifier?.text

  override fun setName(name: String): PsiElement? {
    nameIdentifier?.replace(SolPsiFactory(project).createIdentifier(name))
    return this
  }

  override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

  override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

  override fun getPresentation(): PresentationData = createPresentation()

  override fun getUseScope(): SearchScope = solUseScope(super.getUseScope())
}

// Usage searches (find usages, rename, unused-element checks) look only where a reference can be:
// inside the owner of a local or private declaration, and in Solidity and Yul files for the rest
private fun SolNamedElement.solUseScope(default: SearchScope): SearchScope {
  localOwner()?.let { return LocalSearchScope(it) }
  return when (default) {
    is GlobalSearchScope -> GlobalSearchScope.getScopeRestrictedByFileTypes(default, SolidityFileType, YulFileType)
    else -> default
  }
}

private fun SolNamedElement.localOwner(): PsiElement? = when {
  this is SolParameterDef -> PsiTreeUtil.getParentOfType(
    this,
    SolFunctionDefinition::class.java, SolModifierDefinition::class.java, SolConstructorDefinition::class.java,
    SolCatchClause::class.java, SolTryStatement::class.java, SolFunctionTypeName::class.java,
  )
  // struct members are declared like local variables but are used wherever the struct is
  this is SolVariableDeclaration && parent !is SolStructDefinition ||
    this is SolDeclarationItem || this is SolTypedDeclarationItem -> PsiTreeUtil.getParentOfType(
    this, SolFunctionDefinition::class.java, SolModifierDefinition::class.java, SolConstructorDefinition::class.java,
  )
  this is SolFunctionDefElement && visibility == Visibility.PRIVATE -> contract
  this is SolStateVarElement && visibility == Visibility.PRIVATE -> findContract()
  else -> null
}

private fun SolNamedElement.createPresentation(): PresentationData {
  val location: String? = containingFile?.let { psiFile -> SymbolPresentationUtil.getFilePathPresentation(psiFile) }

  return PresentationData(name, location, getIcon(0), null)
}
