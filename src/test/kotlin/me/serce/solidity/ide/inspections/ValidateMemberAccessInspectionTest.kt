package me.serce.solidity.ide.inspections

class ValidateMemberAccessInspectionTest : SolInspectionsTestBase(ValidateMemberAccessInspection()) {
  fun testErrorSelector() = checkByText("""
        interface IFoo {
            error Bad();
            function go() external;
        }

        contract C {
            error Local(uint256 x);

            function f() external pure returns (bytes4, bytes4, bytes4) {
                return (IFoo.Bad.selector, IFoo.go.selector, Local.selector);
            }
        }
    """, checkWeakWarn = true)

  fun testUnknownErrorMember() = checkByText("""
        contract C {
            error Bad();

            function f() external pure {
                Bad./*@weak_warning descr="Member cannot be resolved"@*/foo/*@/weak_warning@*/;
            }
        }
    """, checkWeakWarn = true)
}
