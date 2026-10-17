package me.serce.solidity.ide.inspections

class NoReturnInspectionTest : SolInspectionsTestBase(NoReturnInspection()) {
  fun testNoReturnVarName() = checkByText("""
        contract a {
            /*@warning descr="No return statement"@*/function a() returns (uint) {
                var test = 5;
            }/*@/warning@*/
        }
    """)

  fun testRevert() = checkByText("""
        contract a {
            function a() returns (uint) {
                revert();
            }
        }
    """)

  fun testAssemblyReturn() = checkByText("""
      contract a {
          function asmReturns(uint _v) public returns (uint) {
              assembly {
                  let _ptr := add(msize(), 1)
                  mstore(_ptr, _v)
                  return(_ptr, 0x20)
              }
          }
      }
  """)

  /*
  fun testRevertShadowing() = checkByText("""
        contract a {
            function revert() {

            }

            /*@warning descr="no return statement"@*/function a() returns (uint) {
                revert();
            }/*@/warning@*/
        }
    """)
  */

  fun testReturnWithIf() = checkByText("""
        contract a {
            function a() returns (uint) {
                if (true) {
                  return 1;
                } else {
                  return 2;
                }
            }
        }
    """)

  fun testThrow() = checkByText("""
        contract a {
            function payReward() returns(bool) {
                if (usnContract.getOriginalClient().DAOrewardAccount().call.value(msg.value)()) {
                    return true;
                } else {
                    throw;
                }
            }
        }
    """)

  fun testAssignmentWithIf() = checkByText("""
        contract a {
            function a() returns (uint result) {
                if (true) {
                  result = 1;
                } else {
                  result = 2;
                }
            }
        }
    """)

  fun testAssignmentWithIfRevert() = checkByText("""
      contract a {
          function test() returns (uint256 result) {
              if (true) {
                  result = 1;
              } else {
                  revert();
              }
          }
      }
  """)

  fun testHasReturnVarName() = checkByText("""
        contract a {
            function a() returns (/*@warning descr="Return variable not assigned"@*/uint a/*@/warning@*/) {
                var test = 5;
            }
        }
    """)

  fun testReturnVarInTuple() = checkByText("""
        contract a {
            function a() returns (uint a) {
                var test = 5;
                (a, test) = (5, 5);
            }
        }
    """)

  fun testAssignmentInAssembly() = checkByText("""
        contract a {
            function lowLevel(string memory source) pure returns (bytes32 result) {
                assembly {
                    result := mload(add(source, 32))
                }
            }
        }
    """)

  fun testAssignmentWithFunctionCall() = checkByText("""
        interface SomeInterface {

        }

        contract a {
            address someAddress;

            function lookupService(bytes32 identifier) pure returns (address manager) {
                manager = SomeInterface(someAddress).getContractAddress(identifier);
            }
        }
    """)

  fun testAssignmentWithRequire() = checkByText("""
        contract Test {
            function testRequire(uint input) external returns (bool result) {
                require((result = input > 0), "input must be greater than 0");
            }
        }
    """)

  fun testAssignmentWithTryCatch() = checkByText("""
      interface ITest {
          function test() external;
      }

      contract Test {
          function testTryCatch() external returns (bool result) {
              try ITest(0x0000000000000000000000000000000000000000).test() {
                  result = true;
              } catch {
                  result = false;
              }
          }
      }
    """)

  fun testReturnWithTryCatch() = checkByText("""
      interface ITest {
          function test() external;
      }

      contract Test {
          function testTryCatch() external returns (bool) {
              try ITest(0x0000000000000000000000000000000000000000).test() {
                  return true;
              } catch {
                  return false;
              }
          }
      }
    """)

  fun testAssemblyReturnInNestedBlock() = checkByText("""
      contract a {
          function hash(uint[3] memory) public pure returns (uint) {
              assembly {
                  {
                      mstore(0, 1)
                      return(0, 0x20)
                  }
              }
          }
      }
  """)

  fun testReturnVariableWrittenThroughAssemblyPointer() = checkByText("""
      contract a {
          struct P { uint x; }
          function f() internal view returns (P memory r) {
              assembly {
                  mstore(r, 1)
              }
          }
      }
  """)

  fun testReturnVariableMembersAssigned() = checkByText("""
      contract a {
          struct P { uint x; uint[2] y; }
          function f() internal pure returns (P memory r) {
              r.x = 1;
              r.y[0] = 2;
          }
      }
  """)

  fun testReturnVariableAssignedInLoop() = checkByText("""
      contract a {
          function f(uint[] calldata words) internal pure returns (uint y) {
              for (uint i = words.length; i > 0; ) {
                  unchecked { --i; }
                  y = y + words[i];
              }
          }
      }
  """)

  fun testReturnVariableNotAssigned() = checkByText("""
      contract a {
          function f() internal pure returns (/*@warning descr="Return variable not assigned"@*/uint y/*@/warning@*/) {
              uint z = 1;
          }
      }
  """)
}
