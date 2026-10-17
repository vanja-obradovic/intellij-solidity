package me.serce.solidity.ide.inspections

class ValidateFunctionArgumentsInspectionTest : SolInspectionsTestBase(ValidateFunctionArgumentsInspection()) {
  fun testTypeAssertFailure() = checkByText("""
        contract a {
            function a() {
                assert(/*@error descr="Argument of type 'string' is not assignable to parameter of type 'bool'"@*/"myString"/*@/error@*/);
            }
        }
    """)

  fun testTypeAssertSuccess() = checkByText("""
        contract a {
            function a() {
                assert(true);
            }
        }
    """)

  fun testWrongNumberOfArguments() = checkByText("""
        contract a {
            function a() {
                b(/*@error descr="Expected 1 argument, but got 2"@*/1, 2/*@/error@*/);
            }
            
            function b(int a) {
            
            }
        }
    """)

  fun testValidationDisabled() = checkByText("""
        contract a {
            function a() {
                b(1, 2);
            }
            
            /**
            * @custom:no_validation
            */
            function b(int a) {
            
            }
        }
    """)

  fun testVarargs() = checkByText("""
        contract a {
            function a() {
                string r = string.concat('a', 'b', 'c');
                string r2 = string.concat(/*@error descr="Argument of type 'uint8' is not assignable to parameter of type 'string'"@*/'a', 2, 'c'/*@/error@*/); 
            }
            
            function b(int a) {
            
            }
        }
    """)

  fun testHexLiteralToBytes() = checkByText("""
        contract a {
            function a() {
                b(hex"01", hex"0102");
            }

            function b(bytes memory x, bytes2 y) {
            }
        }
    """)

  fun testHexLiteralToInteger() = checkByText("""
        contract a {
            function a() {
                b(/*@error descr="Argument of type 'string' is not assignable to parameter of type 'uint8'"@*/hex"01"/*@/error@*/);
            }

            function b(uint8 x) {
            }
        }
    """)
}
