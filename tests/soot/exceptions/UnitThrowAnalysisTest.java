package soot.exceptions;

import soot.*;
import soot.toolkits.scalar.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.grimp.*;
import soot.grimp.internal.*;
import soot.exceptions.*;
import java.util.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import soot.exceptions.ExceptionTestUtility;
import soot.exceptions.ExceptionTestUtility.ExceptionHashSet;

public class UnitThrowAnalysisTest extends TestCase {

    class ImmaculateInvokeUnitThrowAnalysis extends UnitThrowAnalysis {
	// A variant of UnitThrowAnalysis which assumes that
	// invoked methods will never throw any exceptions, rather
	// than that they might throw anything Throwable.  This is to
	// allow us to test that arguments to invocations are being
	// examined.

	ThrowableSet mightThrow(SootMethod m) {
	    return ThrowableSet.Manager.v().EMPTY;
	}
    }

    UnitThrowAnalysis unitAnalysis;
    UnitThrowAnalysis immaculateAnalysis;

    // A collection of Grimp values and expressions used in various tests:
    protected StaticFieldRef floatStaticFieldRef;
    protected Local floatLocal;
    protected FloatConstant floatConstant;
    protected Local floatConstantLocal;
    protected InstanceFieldRef floatInstanceFieldRef;
    protected ArrayRef floatArrayRef;
    protected VirtualInvokeExpr floatVirtualInvoke;
    protected StaticInvokeExpr floatStaticInvoke;

    private ExceptionTestUtility utility;

    public UnitThrowAnalysisTest(String name) {
	super(name);
	unitAnalysis = new UnitThrowAnalysis();
	immaculateAnalysis = new ImmaculateInvokeUnitThrowAnalysis();
    }

    protected void setUp() {
	// Ensure the Exception classes we need are represented in Soot:
	utility = new ExceptionTestUtility("/usr/localcc/pkgs/jdk1.4/jre/lib/rt.jar");

	SootField nanField = new SootField("<java.lang.Float: float NaN>", FloatType.v());
	floatStaticFieldRef = Grimp.v().newStaticFieldRef(nanField);
	floatLocal = Grimp.v().newLocal("local", FloatType.v());
	floatConstant = FloatConstant.v(33.42f);
	floatConstantLocal = Grimp.v().newLocal("local", RefType.v("soot.jimple.FloatConstant"));
	SootField valueField = new SootField("value", FloatType.v());
	floatInstanceFieldRef = Grimp.v().newInstanceFieldRef(floatConstantLocal,
							      valueField);
	floatArrayRef = Grimp.v().newArrayRef(
	    Jimple.v().newLocal("local1", FloatType.v()), 
	    IntConstant.v(0));
	List voidList = new ArrayList();
	floatVirtualInvoke = Grimp.v().newVirtualInvokeExpr(
	    floatConstantLocal, 
	    new SootMethod("floatFunction", voidList, FloatType.v()), 
	    voidList);
	floatStaticInvoke = Grimp.v().newStaticInvokeExpr(
	    new SootMethod("floatFunction", 
			   new ArrayList(Arrays.asList(new Type[] {
			       FloatType.v(), FloatType.v(),
			   })),
			   FloatType.v()),
	    new ArrayList(Arrays.asList(new Value[] {
		floatStaticFieldRef, floatArrayRef,
	    })));
    }


    public void testJInvokeStmt() {
	List voidList = new ArrayList();
	Stmt s = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(
	   new SootMethod("no.such.method", voidList, VoidType.v()), voidList));
	assertTrue(utility.sameMembers(utility.ALL_ERRORS_REPRESENTATION,
				       immaculateAnalysis.mightThrow(s)));
	assertEquals(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(immaculateAnalysis.mightThrow(s)));
	assertTrue(utility.sameMembers(utility.ALL_THROWABLES_REPRESENTATION,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ALL_TEST_THROWABLES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s))); 
    }

    public void testGInvokeStmt() {
	List voidList = new ArrayList();
	Stmt s = Grimp.v().newInvokeStmt(Grimp.v().newStaticInvokeExpr(
	   new SootMethod("no.such.method", voidList, VoidType.v()), voidList));
	assertTrue(utility.sameMembers(utility.ALL_ERRORS_REPRESENTATION,
				       immaculateAnalysis.mightThrow(s)));
	assertEquals(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(immaculateAnalysis.mightThrow(s)));
	assertTrue(utility.sameMembers(utility.ALL_THROWABLES_REPRESENTATION,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ALL_TEST_THROWABLES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJAssignStmt() {

	// local0 = 0
	Stmt s = Jimple.v().newAssignStmt(Jimple.v().newLocal("local0",
							    IntType.v()),
					 IntConstant.v(0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));

	ArrayRef arrayRef = Jimple.v().newArrayRef(
	    Jimple.v().newLocal("local1",
			       ArrayType.v(RefType.v("java.lang.Object"), 1)), 
	    IntConstant.v(0));
	Local scalarRef = Jimple.v().newLocal("local2", 
					     RefType.v("java.lang.Object"));

	// local2 = local1[0]
	s = Jimple.v().newAssignStmt(scalarRef, arrayRef);

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	expectedRep.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, 
				       unitAnalysis.mightThrow(s)));

	Set expectedCatch = 
	    new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
	
	// local1[0] = local2
	s = Jimple.v().newAssignStmt(arrayRef, scalarRef);
	expectedRep.add(utility.ARRAY_STORE_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(s)));
	expectedCatch.add(utility.ARRAY_STORE_EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGAssignStmt() {

	// local0 = 0
	Stmt s = Grimp.v().newAssignStmt(Grimp.v().newLocal("local0",
							    IntType.v()),
					 IntConstant.v(0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));

	ArrayRef arrayRef = Grimp.v().newArrayRef(
	    Grimp.v().newLocal("local1",
			       ArrayType.v(RefType.v("java.lang.Object"), 1)), 
	    IntConstant.v(0));
	Local scalarRef = Grimp.v().newLocal("local2", 
					     RefType.v("java.lang.Object"));

	// local2 = local1[0]
	s = Grimp.v().newAssignStmt(scalarRef, arrayRef);

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	expectedRep.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, 
				       unitAnalysis.mightThrow(s)));
	Set expectedCatch = 
	    new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
	
	// local1[0] = local2
	s = Grimp.v().newAssignStmt(arrayRef, scalarRef);
	expectedRep.add(utility.ARRAY_STORE_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(s)));
	expectedCatch.add(utility.ARRAY_STORE_EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJEnterMonitorStmt() {
	Stmt s = Jimple.v().newEnterMonitorStmt(StringConstant.v("test"));

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGEnterMonitorStmt() {
	Stmt s = Grimp.v().newEnterMonitorStmt(StringConstant.v("test"));

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);

	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJExitMonitorStmt() {
	Stmt s = Jimple.v().newExitMonitorStmt(StringConstant.v("test"));

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGExitMonitorStmt() {
	Stmt s = Grimp.v().newExitMonitorStmt(StringConstant.v("test"));


	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);

	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJIfStmt() {
	IfStmt s = Jimple.v().newIfStmt(Jimple.v().newEqExpr(IntConstant.v(1), 
							     IntConstant.v(1)),
				       (Unit) null);
	s.setTarget(s);		// A very tight infinite loop.
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGIfStmt() {
	IfStmt s = Grimp.v().newIfStmt(Grimp.v().newEqExpr(IntConstant.v(1), 
							   IntConstant.v(1)),
				       (Unit) null);
	s.setTarget(s);		// A very tight infinite loop.
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJLookupSwitchStmt() {
	Stmt target = Jimple.v().newAssignStmt(Jimple.v().newLocal("local0",
								  IntType.v()),
					      IntConstant.v(0));
	Stmt s = Jimple.v().newLookupSwitchStmt(IntConstant.v(1),
					       Arrays.asList(new Value[] {
						   IntConstant.v(1)
					       }),
					       Arrays.asList(new Unit[] {
						   target
					       }),
					       target);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGLookupSwitchStmt() {
	Stmt target = Grimp.v().newAssignStmt(Grimp.v().newLocal("local0",
								  IntType.v()),
					      IntConstant.v(0));
	Stmt s = Grimp.v().newLookupSwitchStmt(IntConstant.v(1),
					       Arrays.asList(new Value[] {
						   IntConstant.v(1)
					       }),
					       Arrays.asList(new Unit[] {
						   target
					       }),
					       target);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJReturnStmt() {
	Stmt s = Jimple.v().newReturnStmt(IntConstant.v(1));

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGReturnStmt() {
	Stmt s = Grimp.v().newReturnStmt(IntConstant.v(1));

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJReturnVoidStmt() {
	Stmt s = Jimple.v().newReturnVoidStmt();

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGReturnVoidStmt() {
	Stmt s = Grimp.v().newReturnVoidStmt();

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(s)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.ILLEGAL_MONITOR_STATE_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJTableSwitchStmt() {
	Stmt target = Jimple.v().newAssignStmt(Jimple.v().newLocal("local0",
								  IntType.v()),
					      IntConstant.v(0));
	Stmt s = Jimple.v().newTableSwitchStmt(IntConstant.v(1), 0, 1,
					       Arrays.asList(new Unit[] {
						   target
					       }),
					       target);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS, 
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGTableSwitchStmt() {
	Stmt target = Grimp.v().newAssignStmt(Grimp.v().newLocal("local0",
								  IntType.v()),
					      IntConstant.v(0));
	Stmt s = Grimp.v().newTableSwitchStmt(IntConstant.v(1), 0, 1,
					       Arrays.asList(new Unit[] {
						   target
					       }),
					       target);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS, 
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testJThrowStmt() {

	// First test with an argument that is included in
	// PERENNIAL_THROW_EXCEPTIONS.
	ThrowStmt s = Jimple.v().newThrowStmt(Jimple.v().newLocal("local0", 
	    RefType.v("java.lang.NullPointerException")));
	Set expectedRep = new ExceptionHashSet(utility.PERENNIAL_THROW_EXCEPTIONS);
	expectedRep.remove(utility.NULL_POINTER_EXCEPTION);
	expectedRep.add(AnySubType.v(utility.NULL_POINTER_EXCEPTION));
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(s)));
	assertEquals(utility.PERENNIAL_THROW_EXCEPTIONS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));

	// Throw a local of type IncompatibleClassChangeError.
	Local local = Jimple.v().newLocal("local1", 
					  utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);
	s.setOp(local);
	expectedRep = new ExceptionHashSet(utility.THROW_PLUS_INCOMPATIBLE_CLASS_CHANGE);
	expectedRep.remove(utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);
	expectedRep.add(AnySubType.v(utility.INCOMPATIBLE_CLASS_CHANGE_ERROR));
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(s)));
	assertEquals(utility.THROW_PLUS_INCOMPATIBLE_CLASS_CHANGE_PLUS_SUBTYPES_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));

	// Throw a local of unknown type.
	local = Jimple.v().newLocal("local1", soot.UnknownType.v());
	s.setOp(local);
	assertTrue(utility.sameMembers(utility.ALL_THROWABLES_REPRESENTATION, 
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ALL_TEST_THROWABLES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }

    public void testGThrowStmt() {
	ThrowStmt s = Grimp.v().newThrowStmt(Grimp.v().newLocal("local0", 
	    RefType.v("java.util.zip.ZipException")));

	Set expectedRep = new ExceptionHashSet(utility.PERENNIAL_THROW_EXCEPTIONS);
	expectedRep.add(AnySubType.v(Scene.v().getRefType("java.util.zip.ZipException")));
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(s)));

	Set expectedCatch = new ExceptionHashSet(utility.PERENNIAL_THROW_EXCEPTIONS_PLUS_SUPERTYPES);
	// We don't need to add java.util.zip.ZipException, since it is not
	// in the universe of test Throwables.
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));

	// Now throw a new IncompatibleClassChangeError.
	s = Grimp.v().newThrowStmt(
		Grimp.v().newNewInvokeExpr(
		    utility.INCOMPATIBLE_CLASS_CHANGE_ERROR,
		    utility.INCOMPATIBLE_CLASS_CHANGE_ERROR.getSootClass().getMethod("void <init>()"),
		    new ArrayList()
		)
	    );
	assertTrue(utility.sameMembers(utility.THROW_PLUS_INCOMPATIBLE_CLASS_CHANGE, 
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.THROW_PLUS_INCOMPATIBLE_CLASS_CHANGE_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    
	// Throw a local of type IncompatibleClassChangeError.
	Local local = Grimp.v().newLocal("local1", 
					 utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);
	s.setOp(local);
	expectedRep = new ExceptionHashSet(utility.PERENNIAL_THROW_EXCEPTIONS);
	expectedRep.remove(utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);
	expectedRep.add(AnySubType.v(utility.INCOMPATIBLE_CLASS_CHANGE_ERROR));
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(s)));
	assertEquals(utility.THROW_PLUS_INCOMPATIBLE_CLASS_CHANGE_PLUS_SUBTYPES_PLUS_SUPERTYPES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));

	// Throw a local of unknown type.
	local = Jimple.v().newLocal("local1", soot.UnknownType.v());
	s.setOp(local);
	assertTrue(utility.sameMembers(utility.ALL_THROWABLES_REPRESENTATION, 
				       unitAnalysis.mightThrow(s)));
	assertEquals(utility.ALL_TEST_THROWABLES, 
		     utility.catchableSubset(unitAnalysis.mightThrow(s)));
    }


    public void testJArrayRef() {
	ArrayRef arrayRef = Jimple.v().newArrayRef(
	    Jimple.v().newLocal("local1",
				ArrayType.v(RefType.v("java.lang.Object"), 1)), 
	    IntConstant.v(0));

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	expectedRep.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, 
				       unitAnalysis.mightThrow(arrayRef)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(arrayRef)));
    }

    public void testGArrayRef() {
	ArrayRef arrayRef = Grimp.v().newArrayRef(
	    Grimp.v().newLocal("local1",
			       ArrayType.v(RefType.v("java.lang.Object"), 1)), 
	    IntConstant.v(0));

	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	expectedRep.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, 
				       unitAnalysis.mightThrow(arrayRef)));

	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch, 
		     utility.catchableSubset(unitAnalysis.mightThrow(arrayRef)));
    }

    public void testJDivExpr() {
	Set asyncAndArithmetic = new ExceptionHashSet(utility.ASYNC_ERRORS);
	asyncAndArithmetic.add(utility.ARITHMETIC_EXCEPTION);
	Set asyncAndArithmeticAndSupertypes = 
	    new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	asyncAndArithmeticAndSupertypes.add(utility.ARITHMETIC_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.RUNTIME_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.EXCEPTION);

	Local intLocal = Jimple.v().newLocal("intLocal", IntType.v());
	Local longLocal = Jimple.v().newLocal("longLocal", LongType.v());
	Local floatLocal = Jimple.v().newLocal("floatLocal", FloatType.v());
	Local doubleLocal = Jimple.v().newLocal("doubleLocal", DoubleType.v());

	DivExpr v = Jimple.v().newDivExpr(intLocal, IntConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(intLocal, IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(IntConstant.v(0), IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(intLocal, intLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(longLocal, LongConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(longLocal, LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(LongConstant.v(0), LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(longLocal, longLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(floatLocal, FloatConstant.v(0.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(floatLocal, FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(FloatConstant.v(0), FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(floatLocal, floatLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(doubleLocal, DoubleConstant.v(0.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(doubleLocal, DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(DoubleConstant.v(0), DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newDivExpr(doubleLocal, doubleLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testGDivExpr() {
	Set asyncAndArithmetic = new ExceptionHashSet(utility.ASYNC_ERRORS);
	asyncAndArithmetic.add(utility.ARITHMETIC_EXCEPTION);
	Set asyncAndArithmeticAndSupertypes = 
	    new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	asyncAndArithmeticAndSupertypes.add(utility.ARITHMETIC_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.RUNTIME_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.EXCEPTION);

	Local intLocal = Grimp.v().newLocal("intLocal", IntType.v());
	Local longLocal = Grimp.v().newLocal("longLocal", LongType.v());
	Local floatLocal = Grimp.v().newLocal("floatLocal", FloatType.v());
	Local doubleLocal = Grimp.v().newLocal("doubleLocal", DoubleType.v());

	DivExpr v = Grimp.v().newDivExpr(intLocal, IntConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(intLocal, IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(IntConstant.v(0), IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(intLocal, intLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(Grimp.v().newAddExpr(intLocal, intLocal),
				 Grimp.v().newMulExpr(intLocal, intLocal));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(longLocal, LongConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(longLocal, LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(LongConstant.v(0), LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(longLocal, longLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(Grimp.v().newAddExpr(longLocal, longLocal),
				 Grimp.v().newMulExpr(longLocal, longLocal));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(floatLocal, FloatConstant.v(0.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(floatLocal, FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(FloatConstant.v(0), FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(floatLocal, floatLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(doubleLocal, DoubleConstant.v(0.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(doubleLocal, DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(DoubleConstant.v(0), DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newDivExpr(doubleLocal, doubleLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testJRemExpr() {
	Set asyncAndArithmetic = new ExceptionHashSet(utility.ASYNC_ERRORS);
	asyncAndArithmetic.add(utility.ARITHMETIC_EXCEPTION);
	Set asyncAndArithmeticAndSupertypes = 
	    new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	asyncAndArithmeticAndSupertypes.add(utility.ARITHMETIC_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.RUNTIME_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.EXCEPTION);

	Local intLocal = Jimple.v().newLocal("intLocal", IntType.v());
	Local longLocal = Jimple.v().newLocal("longLocal", LongType.v());
	Local floatLocal = Jimple.v().newLocal("floatLocal", FloatType.v());
	Local doubleLocal = Jimple.v().newLocal("doubleLocal", DoubleType.v());

	RemExpr v = Jimple.v().newRemExpr(intLocal, IntConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(intLocal, IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(IntConstant.v(0), IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(intLocal, intLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(longLocal, LongConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(longLocal, LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(LongConstant.v(0), LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(longLocal, longLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(floatLocal, FloatConstant.v(0.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(floatLocal, FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(FloatConstant.v(0), FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(floatLocal, floatLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(doubleLocal, DoubleConstant.v(0.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(doubleLocal, DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(DoubleConstant.v(0), DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newRemExpr(doubleLocal, doubleLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testGRemExpr() {
	Set asyncAndArithmetic = new ExceptionHashSet(utility.ASYNC_ERRORS);
	asyncAndArithmetic.add(utility.ARITHMETIC_EXCEPTION);
	Set asyncAndArithmeticAndSupertypes = 
	    new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	asyncAndArithmeticAndSupertypes.add(utility.ARITHMETIC_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.RUNTIME_EXCEPTION);
	asyncAndArithmeticAndSupertypes.add(utility.EXCEPTION);

	Local intLocal = Grimp.v().newLocal("intLocal", IntType.v());
	Local longLocal = Grimp.v().newLocal("longLocal", LongType.v());
	Local floatLocal = Grimp.v().newLocal("floatLocal", FloatType.v());
	Local doubleLocal = Grimp.v().newLocal("doubleLocal", DoubleType.v());

	RemExpr v = Grimp.v().newRemExpr(intLocal, IntConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(intLocal, IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(IntConstant.v(0), IntConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(intLocal, intLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(Grimp.v().newAddExpr(intLocal, intLocal),
				 Grimp.v().newMulExpr(intLocal, intLocal));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(longLocal, LongConstant.v(0));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(longLocal, LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(LongConstant.v(0), LongConstant.v(2));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(longLocal, longLocal);
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(Grimp.v().newAddExpr(longLocal, longLocal),
				 Grimp.v().newMulExpr(longLocal, longLocal));
	assertTrue(utility.sameMembers(asyncAndArithmetic,
				       unitAnalysis.mightThrow(v)));
	assertEquals(asyncAndArithmeticAndSupertypes,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(floatLocal, FloatConstant.v(0.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(floatLocal, FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(FloatConstant.v(0), FloatConstant.v(2.0f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(floatLocal, floatLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(doubleLocal, DoubleConstant.v(0.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(doubleLocal, DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(DoubleConstant.v(0), DoubleConstant.v(2.0));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newRemExpr(doubleLocal, doubleLocal);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testJBinOpExp() {
	Value v = Jimple.v().newAddExpr(IntConstant.v(456), 
					Jimple.v().newLocal("local", IntType.v()));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newOrExpr(Jimple.v().newLocal("local", LongType.v()),
				 LongConstant.v(33));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newLeExpr(Jimple.v().newLocal("local", FloatType.v()),
				 FloatConstant.v(33.42f));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Jimple.v().newEqExpr(DoubleConstant.v(-33.45e-3),
				 Jimple.v().newLocal("local", DoubleType.v()));
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testGBinOpExp() {
	Value v = Grimp.v().newAddExpr(floatStaticFieldRef, floatConstant);
	assertTrue(utility.sameMembers(utility.ALL_ERRORS_REPRESENTATION,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newOrExpr(v, floatConstant);
	assertTrue(utility.sameMembers(utility.ALL_ERRORS_REPRESENTATION,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	Set expectedRep = new ExceptionHashSet(utility.ALL_ERRORS_REPRESENTATION);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);

	Set expectedCatch = new ExceptionHashSet(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);

	v = Grimp.v().newLeExpr(floatInstanceFieldRef, v);
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	v = Grimp.v().newEqExpr(v, floatVirtualInvoke);
	assertTrue(utility.sameMembers(expectedRep, 
				       immaculateAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(immaculateAnalysis.mightThrow(v)));
	assertTrue(utility.sameMembers(utility.ALL_THROWABLES_REPRESENTATION,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ALL_TEST_THROWABLES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	expectedRep.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION);
	expectedCatch.add(utility.INDEX_OUT_OF_BOUNDS_EXCEPTION);

	v = Grimp.v().newNeExpr(v, floatStaticInvoke);
	assertEquals(expectedCatch,
		     utility.catchableSubset(immaculateAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(immaculateAnalysis.mightThrow(v)));
	assertTrue(utility.sameMembers(utility.ALL_THROWABLES_REPRESENTATION,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ALL_TEST_THROWABLES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testJCastExpr() {
	// First an upcast that can be statically proved safe.
	Value v = Jimple.v().newCastExpr(Jimple.v().newLocal("local",
							     utility.INCOMPATIBLE_CLASS_CHANGE_ERROR),
					 utility.LINKAGE_ERROR);
	Set expectedRep = new ExceptionHashSet(utility.ALL_ERRORS_REPRESENTATION);
	Set expectedCatch = new ExceptionHashSet(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES);
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));


	// Then a vacuous cast which can be statically proved safe.
	v = Jimple.v().newCastExpr(Jimple.v().newLocal("local",
						       utility.LINKAGE_ERROR),
				   utility.LINKAGE_ERROR);
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	// Finally a downcast which is not necessarily safe:
	v = Jimple.v().newCastExpr(Jimple.v().newLocal("local",
						       utility.LINKAGE_ERROR),
				   utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);

	expectedRep.add(utility.CLASS_CAST_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, 
				       unitAnalysis.mightThrow(v)));

	expectedCatch.add(utility.CLASS_CAST_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);

	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testGCastExpr() {
	// First an upcast that can be statically proved safe.
	Value v = Grimp.v().newCastExpr(Jimple.v().newLocal("local",
							     utility.INCOMPATIBLE_CLASS_CHANGE_ERROR),
					 utility.LINKAGE_ERROR);
	Set expectedRep = new ExceptionHashSet(utility.ALL_ERRORS_REPRESENTATION);
	Set expectedCatch = new ExceptionHashSet(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES);
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));


	// Then a vacuous cast which can be statically proved safe.
	v = Jimple.v().newCastExpr(Jimple.v().newLocal("local",
						       utility.LINKAGE_ERROR),
				   utility.LINKAGE_ERROR);
	assertTrue(utility.sameMembers(expectedRep, unitAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));

	// Finally a downcast which is not necessarily safe:
	v = Jimple.v().newCastExpr(Jimple.v().newLocal("local",
						       utility.LINKAGE_ERROR),
				   utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);

	expectedRep.add(utility.CLASS_CAST_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep, 
				       unitAnalysis.mightThrow(v)));

	expectedCatch.add(utility.CLASS_CAST_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);

	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testGInstanceFieldRef() {
	Local local = Grimp.v().newLocal("local", 
					 utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);

	Set expectedRep = new ExceptionHashSet(utility.ALL_ERRORS_REPRESENTATION);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);

	Set expectedCatch = new ExceptionHashSet(utility.ALL_TEST_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);

	SootField field = utility.THROWABLE.getSootClass().getFieldByName("detailMessage");
	Value v = Grimp.v().newInstanceFieldRef(local, field);
	assertTrue(utility.sameMembers(expectedRep, 
				       unitAnalysis.mightThrow(v)));
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }


    public void testStringConstant() {
	Value v = StringConstant.v("test");
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(v)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(v)));
    }

    public void testJLocal() {
	Local local = Jimple.v().newLocal("local1", 
					  utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(local)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(local)));
    }

    public void testGLocal() {
	Local local = Grimp.v().newLocal("local1", 
					 utility.INCOMPATIBLE_CLASS_CHANGE_ERROR);
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(local)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(local)));
    }

    public void testBAddInst() {
	soot.baf.AddInst i = soot.baf.Baf.v().newAddInst(IntType.v());
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(i)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(i)));
    }
	
    public void testBAndInst() {
	soot.baf.AndInst i = soot.baf.Baf.v().newAndInst(IntType.v());
	assertTrue(utility.sameMembers(utility.ASYNC_ERRORS,
				       unitAnalysis.mightThrow(i)));
	assertEquals(utility.ASYNC_ERRORS_PLUS_SUPERTYPES,
		     utility.catchableSubset(unitAnalysis.mightThrow(i)));
    }
	
    public void testBArrayLengthInst() {
	soot.baf.ArrayLengthInst i = soot.baf.Baf.v().newArrayLengthInst();
	Set expectedRep = new ExceptionHashSet(utility.ASYNC_ERRORS);
	expectedRep.add(utility.NULL_POINTER_EXCEPTION);
	assertTrue(utility.sameMembers(expectedRep,
				       unitAnalysis.mightThrow(i)));
	Set expectedCatch = new ExceptionHashSet(utility.ASYNC_ERRORS_PLUS_SUPERTYPES);
	expectedCatch.add(utility.NULL_POINTER_EXCEPTION);
	expectedCatch.add(utility.RUNTIME_EXCEPTION);
	expectedCatch.add(utility.EXCEPTION);
	assertEquals(expectedCatch,
		     utility.catchableSubset(unitAnalysis.mightThrow(i)));
    }
	

    public static Test suite() {
	return new TestSuite(UnitThrowAnalysisTest.class);
    }    

    public static void main(String arg[]) {
	junit.textui.TestRunner.run(suite());
    }
}

