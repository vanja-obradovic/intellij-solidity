package me.serce.solidity.ide.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.tree.util.children
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import me.serce.solidity.ide.colors.SolColor
import me.serce.solidity.ide.hints.startOffset
import me.serce.solidity.lang.core.SolidityTokenTypes
import me.serce.solidity.lang.psi.*
import me.serce.solidity.lang.psi.impl.SolErrorDefMixin
import me.serce.solidity.lang.resolve.SolResolver
import me.serce.solidity.lang.types.isBuiltin

class SolidityAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is SolElement) {
      highlight(element, holder)
    }
  }

  private fun highlight(element: SolElement, holder: AnnotationHolder) {
    fun keyword() = applyColor(holder, element, SolColor.KEYWORD)
    when (element) {
      is SolParameterDef -> {
        element.identifier?.let {
          handleParameterDef(element, element.identifier!!, holder)
        }
      }
      is SolImportDirective -> element.node.children().find { it.text == "from" }
        ?.let { applyColor(holder, it.textRange, SolColor.KEYWORD) }
      is SolNumberType -> applyColor(holder, element, SolColor.TYPE)
      is SolElementaryTypeName -> applyColor(holder, element, SolColor.TYPE)
      is SolStateMutabilitySpecifier -> if (element.text == "payable") keyword()
      is SolEnumValue -> applyColor(holder, element, SolColor.ENUM_VALUE)
      is SolMemberAccessExpression -> {
        when (element.expression.firstChild.text) {
          "super" -> applyColor(holder, element.expression.firstChild, SolColor.KEYWORD)
          "msg", "block", "abi" -> applyColor(holder, element.expression.firstChild, SolColor.GLOBAL)
        }

        val elementRef: PsiElement? = element.reference?.resolve()
        val color = when {
          elementRef == null -> null
          elementRef.isBuiltin() -> SolColor.BUILTIN_MEMBER
          elementRef is SolFunctionDefinition -> SolColor.FUNCTION_CALL
          elementRef is SolEnumValue -> SolColor.ENUM_VALUE
          elementRef is SolEnumDefinition -> SolColor.ENUM_NAME
          elementRef is SolStructDefinition -> SolColor.STRUCT_NAME
          elementRef is SolContractDefinition -> contractColor(elementRef)
          else -> variableColor(elementRef)
        }
        color?.let { applyColor(holder, element.referenceNameElement, it) }
      }
      is SolErrorDefMixin -> {
        applyColor(holder, element.identifier, SolColor.KEYWORD)
        element.nameIdentifier?.let { applyColor(holder, it, SolColor.ERROR_NAME) }
      }
      is SolHexLiteral -> {
        if ((element.textRange.endOffset - element.startOffset) > 4) {
          applyColor(holder, TextRange(element.startOffset, element.startOffset + 3), SolColor.KEYWORD)
          applyColor(holder, TextRange(element.startOffset + 3, element.textRange.endOffset), SolColor.STRING)
        } else {
          applyColor(holder, element, SolColor.STRING)
        }
      }
      is SolRevertStatement -> applyColor(holder, element.firstChild, SolColor.KEYWORD)
      is SolOverrideSpecifier -> {
        if ((element.textRange.endOffset - element.startOffset) > 8) {
          applyColor(holder, TextRange(element.startOffset, element.startOffset + 8), SolColor.KEYWORD)
        }
      }
      is SolContractDefinition -> element.identifier?.let { applyColor(holder, it, contractColor(element)) }
      is SolStructDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.STRUCT_NAME) }
      is SolEnumDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.ENUM_NAME) }
      is SolEventDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.EVENT_NAME) }
      is SolUserDefinedValueTypeDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.USER_DEFINED_VALUE_TYPE) }
      is SolConstantVariableDeclaration -> applyColor(holder, element.identifier, SolColor.CONSTANT)
      is SolStateVariableDeclaration -> variableColor(element)?.let { applyColor(holder, element.identifier, it) }
      is SolVariableDeclaration -> element.identifier?.let { id -> variableColor(element)?.let { applyColor(holder, id, it) } }
      // `(a, b) = …` parses as an untyped declaration list, but since Solidity 0.5 it assigns existing variables
      is SolDeclarationItem -> element.identifier?.let { id ->
        val assigned = SolResolver.lexicalDeclarations(element).firstOrNull { it !is SolDeclarationItem && it.name == element.name }
        val color = assigned?.let { variableColor(it) } ?: SolColor.LOCAL_VARIABLE
        applyColor(holder, id, color)
        if (color == SolColor.STATE_VARIABLE) applyColor(holder, id, SolColor.STATE_VARIABLE_WRITE)
      }
      is SolTypedDeclarationItem -> element.identifier?.let { applyColor(holder, it, SolColor.LOCAL_VARIABLE) }
      is SolFunctionDefinition -> {
        val identifier = element.identifier
        if (identifier !== null) {
          applyColor(holder, identifier, SolColor.FUNCTION_DECLARATION)
        } else {
          val firstChildNode = element.node.firstChildNode
          if (firstChildNode.text == "receive" || firstChildNode.text == "fallback") {
            applyColor(holder, firstChildNode.textRange, SolColor.RECEIVE_FALLBACK_DECLARATION)
          }
        }
      }
      is SolModifierDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.MODIFIER) }
      // base constructor calls share this syntax; those are colored as the contract they name
      is SolModifierInvocation -> when (element.varLiteral.reference?.resolve()) {
        null, is SolModifierDefinition -> applyColor(holder, element.varLiteral.identifier, SolColor.MODIFIER)
        else -> {}
      }
      is SolUserDefinedTypeName -> {
        when(val type = SolResolver.resolveTypeNameUsingImports(element).firstOrNull()) {
          is SolContractDefinition -> applyColor(holder, element, contractColor(type))
          is SolStructDefinition -> applyColor(holder, element, SolColor.STRUCT_NAME)
          is SolEnumDefinition -> applyColor(holder, element, SolColor.ENUM_NAME)
          is SolUserDefinedValueTypeDefinition -> applyColor(holder, element, SolColor.USER_DEFINED_VALUE_TYPE)
        }
      }
      is SolFunctionCallElement -> {
        when (element.firstChild.text) {
          "keccak256" -> applyColor(holder, element.firstChild, SolColor.GLOBAL_FUNCTION_CALL)
          "require" -> applyColor(holder, element.firstChild, SolColor.KEYWORD)
          "assert" -> applyColor(holder, element.firstChild, SolColor.KEYWORD)
          else -> when (SolResolver.resolveTypeNameUsingImports(element).firstOrNull()) {
            is SolErrorDefinition -> applyColor(holder, element.referenceNameElement, SolColor.ERROR_NAME)
            is SolEventDefinition -> applyColor(holder, element.referenceNameElement, SolColor.EVENT_NAME)
            else -> element.firstChild.let {
              val callee = element.referenceNameElement.reference?.resolve()
              if (it is SolPrimaryExpression && SolResolver.resolveTypeNameUsingImports(element.firstChild)
                  .filterIsInstance<SolStructDefinition>().isNotEmpty()
              ) {
                applyColor(holder, element.referenceNameElement, SolColor.STRUCT_NAME)
              } else if (callee is SolContractDefinition) {
                applyColor(holder, element.referenceNameElement, contractColor(callee))
              } else if (callee != null && callee.isBuiltin()) {
                val color = if (it is SolMemberAccessExpression) SolColor.BUILTIN_MEMBER else SolColor.GLOBAL_FUNCTION_CALL
                applyColor(holder, element.referenceNameElement, color)
              } else {
                applyColor(holder, element.referenceNameElement, SolColor.FUNCTION_CALL)
              }
            }
          }
        }
      }
      is SolYulVariableDeclaration -> {
        applyColor(holder, element.firstChild, SolColor.KEYWORD)
        element.declaredIdentifiers().forEach { applyColor(holder, it, SolColor.LOCAL_VARIABLE) }
      }
      is SolYulFunctionDefinition -> element.declaredIdentifiers().forEachIndexed { i, id ->
        applyColor(holder, id, if (i == 0) SolColor.FUNCTION_DECLARATION else SolColor.FUNCTION_PARAMETER)
      }
      is SolYulSwitchStatement, is SolYulSwitchCase,
      is SolYulObject, is SolYulObjectCode, is SolYulObjectData ->
        applyColor(holder, element.firstChild, SolColor.KEYWORD)
      is SolYulLeave, is SolYulBreak, is SolYulContinue, is SolYulDefault -> keyword()
      is SolYulFunctionCall -> applyColor(
        holder, element.firstChild,
        if (element.firstChild.text in yulBuiltins) SolColor.YUL_BUILTIN else SolColor.FUNCTION_CALL
      )
      is SolLayoutAt -> keyword()
      is SolMutationModifier -> keyword() // transient
      is SolPragmaDirective -> {
        element.node.children().find { it.text == "solidity" }
          ?.let { applyColor(holder, it.textRange, SolColor.KEYWORD) }
      }
      is SolVarLiteral, is SolYulPath -> {
        if (additionalKeywordList().contains(element.text)) {
          applyColor(holder, element, SolColor.KEYWORD)
        } else if (globalKeywordList().contains(element.text)) {
          applyColor(holder, element, SolColor.GLOBAL)
        } else if (element.text == "keccak256") {
          applyColor(holder, element, SolColor.GLOBAL_FUNCTION_CALL)
        } else {
          val elementRef = element.reference?.resolve()
          val color = when {
            elementRef == null -> (element as? SolYulPath)?.let { yulDeclarationColor(it) }
            elementRef.isBuiltin() -> if (elementRef is SolFunctionDefinition) SolColor.GLOBAL_FUNCTION_CALL else SolColor.GLOBAL
            elementRef is SolContractDefinition -> contractColor(elementRef)
            // a base contract imported under an alias resolves to the alias in the inheritance list
            elementRef is SolUserDefinedTypeName ->
              (elementRef.reference?.resolve() as? SolContractDefinition)?.let { contractColor(it) }
            elementRef is SolFunctionDefinition -> SolColor.FUNCTION_CALL
            elementRef is SolModifierDefinition -> SolColor.MODIFIER
            elementRef is SolErrorDefinition -> SolColor.ERROR_NAME
            elementRef is SolEventDefinition -> SolColor.EVENT_NAME
            elementRef is SolEnumDefinition -> SolColor.ENUM_NAME
            elementRef is SolStructDefinition -> SolColor.STRUCT_NAME
            else -> variableColor(elementRef)
          }
          color?.let { applyColor(holder, element, it) }
          if (color == SolColor.STATE_VARIABLE && element is SolVarLiteral && isStorageWrite(element)) {
            applyColor(holder, element, SolColor.STATE_VARIABLE_WRITE)
          }
        }
      }
    }
  }

  private fun contractColor(contract: SolContractDefinition) = when (contract.contractType) {
    ContractType.INTERFACE -> SolColor.INTERFACE_NAME
    ContractType.LIBRARY -> SolColor.LIBRARY_NAME
    ContractType.COMMON -> SolColor.CONTRACT_NAME
  }

  // storage-located locals and parameters point into storage, so they share the state variable color
  private fun variableColor(declaration: PsiElement): SolColor? = when (declaration) {
    is SolConstantVariableDeclaration -> SolColor.CONSTANT
    is SolStateVariableDeclaration -> when (declaration.mutability) {
      VariableMutability.CONSTANT -> SolColor.CONSTANT
      VariableMutability.IMMUTABLE -> SolColor.IMMUTABLE
      null -> SolColor.STATE_VARIABLE
    }
    is SolParameterDef -> if (isStorageElement(declaration)) SolColor.STATE_VARIABLE else SolColor.FUNCTION_PARAMETER
    is SolVariableDeclaration -> when {
      declaration.parent is SolStructDefinition -> SolColor.STRUCT_MEMBER
      isStorageElement(declaration) -> SolColor.STATE_VARIABLE
      else -> SolColor.LOCAL_VARIABLE
    }
    is SolTypedDeclarationItem -> SolColor.LOCAL_VARIABLE
    else -> null
  }

  // `x = …`, `x += …`, `x[i] = …`, `x.f = …`, `(x, y) = …`, `x++`, `delete x`, `x.push(…)`, `x.pop()`
  private fun isStorageWrite(literal: SolVarLiteral): Boolean {
    var target: PsiElement = literal.parent as? SolPrimaryExpression ?: return false
    while (true) {
      val parent = target.parent
      target = when {
        parent is SolMemberAccessExpression && parent.expression == target -> {
          if (parent.identifier?.text in arrayMutators && parent.parent is SolFunctionCallExpression) return true
          parent
        }
        parent is SolIndexAccessExpression && parent.expressionList.firstOrNull() == target -> parent
        parent is SolSeqExpression -> parent
        else -> break
      }
    }
    return when (val parent = target.parent) {
      is SolAssignmentExpression -> parent.expressionList.firstOrNull() == target
      is SolTupleStatement -> parent.expressionList.firstOrNull() == target
      is SolIncrementExpression -> true
      is SolUnaryExpression -> parent.firstChild.text in setOf("++", "--", "delete")
      else -> false
    }
  }

  // assembly variables are declared with `let`, as function arguments and returns, or in a for loop's init block
  private fun yulDeclarationColor(path: SolYulPath): SolColor? {
    val name = path.text.takeIf { '.' !in it } ?: return null
    for (scope in generateSequence(path.parent) { it.parent }) {
      when (scope) {
        is SolYulBlock -> if (scope.declaresYulVariable(name)) return SolColor.LOCAL_VARIABLE
        is SolYulForStatement -> if (scope.yulBlockList.firstOrNull()?.declaresYulVariable(name) == true) return SolColor.LOCAL_VARIABLE
        is SolYulFunctionDefinition -> if (scope.declaredIdentifiers().drop(1).any { it.text == name }) return SolColor.FUNCTION_PARAMETER
        is SolInlineAssemblyStatement -> return null
      }
    }
    return null
  }

  private fun SolYulBlock.declaresYulVariable(name: String) =
    yulStatementList.any { st -> st.yulVariableDeclaration?.declaredIdentifiers()?.any { it.text == name } == true }

  // the identifiers a Yul declaration introduces are its direct identifier tokens after the leading keyword,
  // which the lexer may also emit as an identifier (`let`)
  private fun PsiElement.declaredIdentifiers(): List<PsiElement> =
    generateSequence(firstChild?.nextSibling) { it.nextSibling }.filter { it.elementType == SolidityTokenTypes.IDENTIFIER }.toList()

  private fun isStorageElement(element: PsiElement): Boolean {
    return element.firstChild.childrenOfType<SolStorageLocationSpecifier>().firstOrNull()?.text == "storage"
  }

  private fun handleParameterDef(parameterDef: SolParameterDef, element: PsiElement, holder: AnnotationHolder) {
    if (isStorageElement(parameterDef)) {
      applyColor(
        holder, element, SolColor.STATE_VARIABLE
      )
    } else {
      applyColor(
        holder, element, SolColor.FUNCTION_PARAMETER
      )
    }
  }

  private fun additionalKeywordList(): List<String> {
    return listOf("this", "require", "assert", "super")
  }

  private fun globalKeywordList(): List<String> {
    return listOf("msg", "block", "tx", "abi")
  }

  private val arrayMutators = setOf("push", "pop")

  // Keep in sync with YulEVMBuiltin in solidity.bnf
  private val yulBuiltins = setOf(
    "stop", "add", "sub", "mul", "div", "sdiv", "mod", "smod", "exp", "not",
    "lt", "gt", "slt", "sgt", "eq", "iszero", "and", "or", "xor", "byte",
    "shl", "shr", "sar", "clz", "addmod", "mulmod", "signextend", "keccak256",
    "pop", "mload", "mstore", "mstore8", "sload", "sstore", "tload", "tstore", "msize", "gas",
    "address", "balance", "selfbalance", "caller", "callvalue", "calldataload",
    "calldatasize", "calldatacopy", "extcodesize", "extcodecopy", "returndatasize",
    "returndatacopy", "mcopy", "extcodehash", "create", "create2", "call", "callcode",
    "delegatecall", "staticcall", "return", "revert", "selfdestruct", "invalid",
    "log0", "log1", "log2", "log3", "log4", "chainid", "origin", "gasprice",
    "blockhash", "blobhash", "coinbase", "timestamp", "number", "slotnum", "difficulty", "prevrandao", "gaslimit",
    "basefee", "blobbasefee",
  )

  private fun applyColor(holder: AnnotationHolder, element: PsiElement, color: SolColor) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(element)
      .textAttributes(color.textAttributesKey)
      .create()
  }

  private fun applyColor(holder: AnnotationHolder, range: TextRange, color: SolColor) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(range)
      .textAttributes(color.textAttributesKey)
      .create()
  }
}
