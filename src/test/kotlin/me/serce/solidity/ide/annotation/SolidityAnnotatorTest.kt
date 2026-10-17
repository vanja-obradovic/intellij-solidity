package me.serce.solidity.ide.annotation

import me.serce.solidity.ide.colors.SolColor
import me.serce.solidity.utils.SolTestBase

class SolidityAnnotatorTest : SolTestBase() {
  private val code = """
      pragma solidity ^0.8.28;

      interface IVault { function deposit(uint256 amount) external; }
      library MathLib { function twice(uint256 x) internal pure returns (uint256) { return x * 2; } }
      uint256 constant FILE_LIMIT = 10;

      contract Base { constructor(uint256 v) {} }

      contract Sample is Base {
          using MathLib for uint256;
          uint256 public constant LIMIT = 100;
          address public immutable owner;
          uint256 private total;
          uint256 private other;
          uint256[] private list;
          mapping(address => uint256) balances;
          struct Note { uint256 amount; address to; }
          Note[] private notes;

          modifier onlyOwner() { require(msg.sender == owner, "no"); _; }

          constructor() Base(1) { owner = msg.sender; }

          function pay(IVault vault, uint256 amount) external onlyOwner returns (uint256 left) {
              uint256 doubled = amount.twice();
              Note memory copy = Note(doubled, tx.origin);
              copy.amount = 1;
              Note storage kept = notes[0];
              kept.amount = 2;
              if (doubled > LIMIT + FILE_LIMIT) revert();
              uint256 read = total;
              total += doubled;
              total++;
              delete other;
              (total, other) = (1, 2);
              balances[copy.to] = block.timestamp + gasleft() + read;
              list.push(1);
              vault.deposit(doubled);
              bytes4 sel = this.pay.selector;
              uint256 top = type(uint256).max;
              assembly {
                  function double(v) -> r { r := mul(v, 2) }
                  let ptr := mload(0x40)
                  mstore(ptr, double(doubled))
                  left := add(ptr, 1)
              }
          }
      }
  """

  // the colors annotated on the `occurrence`-th whole-word match of `word`
  private fun colorsAt(word: String, occurrence: Int = 0): Set<SolColor> {
    val text = myFixture.file.text
    val offset = Regex("\\b${Regex.escape(word)}\\b").findAll(text).elementAtOrNull(occurrence)?.range?.first
      ?: error("no occurrence $occurrence of $word")
    return myFixture.doHighlighting()
      .filter { it.startOffset == offset && it.endOffset == offset + word.length }
      .mapNotNull { info -> SolColor.entries.find { it.textAttributesKey == info.forcedTextAttributesKey } }
      .toSet()
  }

  private fun assertColor(color: SolColor, word: String, occurrence: Int = 0) =
    assertTrue("$word#$occurrence: ${colorsAt(word, occurrence)}", color in colorsAt(word, occurrence))

  private fun assertNoColor(color: SolColor, word: String, occurrence: Int = 0) =
    assertFalse("$word#$occurrence: ${colorsAt(word, occurrence)}", color in colorsAt(word, occurrence))

  override fun setUp() {
    super.setUp()
    InlineFile(code)
  }

  fun testLocalVariables() {
    assertColor(SolColor.LOCAL_VARIABLE, "doubled", 0)
    assertColor(SolColor.LOCAL_VARIABLE, "doubled", 1)
    assertColor(SolColor.LOCAL_VARIABLE, "copy", 1)
    assertColor(SolColor.STATE_VARIABLE, "kept", 0)
  }

  fun testConstantsAndImmutables() {
    assertColor(SolColor.CONSTANT, "LIMIT", 0)
    assertColor(SolColor.CONSTANT, "LIMIT", 1)
    assertColor(SolColor.CONSTANT, "FILE_LIMIT", 1)
    assertColor(SolColor.IMMUTABLE, "owner", 0)
    assertColor(SolColor.IMMUTABLE, "owner", 1)
  }

  fun testBuiltins() {
    assertColor(SolColor.GLOBAL, "msg")
    assertColor(SolColor.BUILTIN_MEMBER, "sender")
    assertColor(SolColor.GLOBAL, "tx")
    assertColor(SolColor.BUILTIN_MEMBER, "origin")
    assertColor(SolColor.GLOBAL, "block")
    assertColor(SolColor.BUILTIN_MEMBER, "timestamp")
    assertColor(SolColor.GLOBAL_FUNCTION_CALL, "gasleft")
    assertColor(SolColor.BUILTIN_MEMBER, "selector")
    assertColor(SolColor.BUILTIN_MEMBER, "max")
  }

  fun testAssembly() {
    assertColor(SolColor.YUL_BUILTIN, "mload")
    assertColor(SolColor.YUL_BUILTIN, "mstore")
    assertColor(SolColor.FUNCTION_DECLARATION, "double", 0)
    assertColor(SolColor.FUNCTION_CALL, "double", 1)
    assertColor(SolColor.FUNCTION_PARAMETER, "v", 1)
    assertColor(SolColor.FUNCTION_PARAMETER, "v", 2)
    assertColor(SolColor.LOCAL_VARIABLE, "ptr", 0)
    assertColor(SolColor.LOCAL_VARIABLE, "ptr", 1)
    assertNoColor(SolColor.LOCAL_VARIABLE, "let")
    assertColor(SolColor.LOCAL_VARIABLE, "doubled", 5)
  }

  fun testModifiersInterfacesLibraries() {
    assertColor(SolColor.MODIFIER, "onlyOwner", 0)
    assertColor(SolColor.MODIFIER, "onlyOwner", 1)
    assertColor(SolColor.CONTRACT_NAME, "Base", 2)
    assertNoColor(SolColor.MODIFIER, "Base", 2)
    assertColor(SolColor.INTERFACE_NAME, "IVault", 0)
    assertColor(SolColor.INTERFACE_NAME, "IVault", 1)
    assertColor(SolColor.LIBRARY_NAME, "MathLib", 0)
    assertColor(SolColor.LIBRARY_NAME, "MathLib", 1)
  }

  fun testStorageWrites() {
    assertColor(SolColor.STATE_VARIABLE_WRITE, "total", 2)
    assertColor(SolColor.STATE_VARIABLE_WRITE, "total", 3)
    assertColor(SolColor.STATE_VARIABLE_WRITE, "other", 1)
    assertColor(SolColor.STATE_VARIABLE_WRITE, "total", 4)
    assertColor(SolColor.STATE_VARIABLE_WRITE, "other", 2)
    assertColor(SolColor.STATE_VARIABLE_WRITE, "balances", 1)
    assertColor(SolColor.STATE_VARIABLE_WRITE, "list", 1)
    assertColor(SolColor.STATE_VARIABLE_WRITE, "kept", 1)
    assertColor(SolColor.STATE_VARIABLE, "total", 2)
  }

  fun testReadsAndNonStorageWritesAreNotStorageWrites() {
    assertNoColor(SolColor.STATE_VARIABLE_WRITE, "total", 1)
    assertNoColor(SolColor.STATE_VARIABLE_WRITE, "owner", 2)
    assertNoColor(SolColor.STATE_VARIABLE_WRITE, "copy", 1)
    assertNoColor(SolColor.STATE_VARIABLE_WRITE, "notes", 1)
  }

  fun testSettingsPreviewUsesEveryColor() {
    val demo = javaClass.classLoader.getResource("me/serce/solidity/ide/colors/highlighter_example.sol")!!.readText()
    val tags = Regex("<([A-Z_]+)>").findAll(demo).map { it.groupValues[1] }.toSet()
    val names = SolColor.entries.map { it.name }.toSet()
    assertEmpty(tags - names)
    val lexerOnly = setOf("LINE_COMMENT", "NAT_SPEC_TAG", "BRACES", "BRACKETS", "PARENTHESES", "SEMICOLON", "NUMBER", "OPERATION_SIGN")
    assertEmpty(names - lexerOnly - tags)
  }
}
