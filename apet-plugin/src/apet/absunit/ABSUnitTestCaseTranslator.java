package apet.absunit;

import static abs.backend.tests.AbsASTBuilderUtil.generateImportAST;
import static abs.backend.tests.AbsASTBuilderUtil.getCall;
import static abs.backend.tests.AbsASTBuilderUtil.getDecl;
import static abs.backend.tests.AbsASTBuilderUtil.getExpStmt;
import static abs.backend.tests.AbsASTBuilderUtil.getUnit;
import static abs.backend.tests.AbsASTBuilderUtil.getVAssign;
import static abs.backend.tests.AbsASTBuilderUtil.getVarDecl;
import static abs.backend.tests.AbsASTBuilderUtil.namePred;
import static abs.backend.tests.AbsASTBuilderUtil.newObj;
import static apet.testCases.ABSTestCaseExtractor.getABSDataType;
import static apet.testCases.ABSTestCaseExtractor.getABSDataValue;
import static apet.testCases.ABSTestCaseExtractor.getABSObjectFields;
import static apet.testCases.ABSTestCaseExtractor.getABSObjectType;
import static apet.testCases.ABSTestCaseExtractor.getABSTermArgs;
import static apet.testCases.ABSTestCaseExtractor.getABSTermFunctor;
import static apet.testCases.ABSTestCaseExtractor.getAfterState;
import static apet.testCases.ABSTestCaseExtractor.getInitialState;
import static apet.testCases.ABSTestCaseExtractor.getInputArgs;
import static apet.testCases.ABSTestCaseExtractor.getReturnData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import abs.backend.prettyprint.DefaultABSFormatter;
import abs.backend.tests.AbsASTBuilderUtil;
import abs.backend.tests.AbsASTBuilderUtil.DeclNamePredicate;
import abs.backend.tests.AbsASTBuilderUtil.MethodNamePredicate;
import abs.backend.tests.AbsASTBuilderUtil.MethodSigNamePredicate;
import abs.backend.tests.AbsASTBuilderUtil.ModifyClassModifierNamePredicate;
import abs.backend.tests.AbsASTBuilderUtil.Predicate;
import abs.common.StringUtils;
import abs.frontend.analyser.SemanticError;
import abs.frontend.analyser.SemanticErrorList;
import abs.frontend.ast.Access;
import abs.frontend.ast.AddInterfaceModifier;
import abs.frontend.ast.AddMethodModifier;
import abs.frontend.ast.Annotation;
import abs.frontend.ast.Block;
import abs.frontend.ast.Call;
import abs.frontend.ast.ClassDecl;
import abs.frontend.ast.ClassOrIfaceModifier;
import abs.frontend.ast.DataConstructor;
import abs.frontend.ast.DataConstructorExp;
import abs.frontend.ast.DataTypeDecl;
import abs.frontend.ast.DataTypeUse;
import abs.frontend.ast.Decl;
import abs.frontend.ast.DeltaClause;
import abs.frontend.ast.DeltaDecl;
import abs.frontend.ast.Deltaspec;
import abs.frontend.ast.Feature;
import abs.frontend.ast.FieldDecl;
import abs.frontend.ast.FieldUse;
import abs.frontend.ast.FnApp;
import abs.frontend.ast.FunctionDecl;
import abs.frontend.ast.InitBlock;
import abs.frontend.ast.IntLiteral;
import abs.frontend.ast.InterfaceDecl;
import abs.frontend.ast.InterfaceTypeUse;
import abs.frontend.ast.MethodImpl;
import abs.frontend.ast.MethodSig;
import abs.frontend.ast.Model;
import abs.frontend.ast.ModifyClassModifier;
import abs.frontend.ast.ModifyMethodModifier;
import abs.frontend.ast.ModuleDecl;
import abs.frontend.ast.Opt;
import abs.frontend.ast.ParamDecl;
import abs.frontend.ast.ParametricDataTypeDecl;
import abs.frontend.ast.Product;
import abs.frontend.ast.ProductLine;
import abs.frontend.ast.PureExp;
import abs.frontend.ast.RemoveMethodModifier;
import abs.frontend.ast.ReturnStmt;
import abs.frontend.ast.StarExport;
import abs.frontend.ast.StarImport;
import abs.frontend.ast.StringLiteral;
import abs.frontend.ast.SyncCall;
import abs.frontend.ast.TypeSynDecl;
import abs.frontend.ast.VarUse;
import abs.frontend.tests.ABSFormatter;
import apet.testCases.ABSData;
import apet.testCases.ABSObject;
import apet.testCases.ABSRef;
import apet.testCases.ABSTerm;
import apet.testCases.ApetTestSuite;
import apet.testCases.TestCase;


public class ABSUnitTestCaseTranslator {
	
	private static final String INITIAL_TEST_PREFIX = "initialForTest";
	private static final String ASSERT_TEST_PREFIX = "assertForTest";
	private static final String SETTER_PREFIX = "setForTest";
	private static final String GETTER_PREFIX = "getForTest";
	private static final String RUN_METHOD = "run";
	private static final String CLASS_SUFFIX = "Impl";
	private static final String INTERFACE_SUFFIX = "Test";
	private static final String METHOD_PREFIX = "test";
	private static final String DELTA_SUFFIX = "Delta";
	private static final String CLASS_FUNCTION_PREFIX = "ClassFunctionFor";
	
	private static final String MAIN = "AbsUnit.TestCase";

	private static final String ASSERT_HELPER = "absAssertHelper";
	
	private static final String PRODUCT_NAME = "ABSUnitProduct";
	private static final String CONFIGURATION_NAME = "ABSUnitConfiguration";
	private static final String FEATURE_NAME = "F";
	
	private static final String ignore = "AbsUnit.Ignored";
	private static final String test = "AbsUnit.Test";
	private static final String dataPoint = "AbsUnit.DataPoint";

	private static final String suite = "AbsUnit.Suite";
	private static final String fixture = "AbsUnit.Fixture";

	private DataConstructor ignoreType;
	private DataConstructor testType;
	private DataConstructor dataPointType;

	private DataConstructor suiteType;
	private DataConstructor fixtureType;
	
	private ClassDecl absAssertImpl;

	private final Model model;
	private final ModuleDecl output;
	
	private File outputFile;
	
	private final boolean verbose;
	
	public ABSUnitTestCaseTranslator(Model model, File outputFile, boolean verbose) {
		if (model == null)
			throw new IllegalArgumentException("Model cannot be null!");

		this.model = model;
		this.output = new ModuleDecl();
		this.output.setName(MAIN);
		this.verbose = verbose;
		
		if (verbose) {
			System.out.println("Gathering ABSUnit annotations");
		}
		
		gatherABSUnitAnnotations();

		/*
		 * Do not search for test class definitions if this model does not
		 * contain the necessary ABSUnit annotations
		 */
		if (ignoreType == null || testType == null || dataPointType == null ||
			suiteType == null || fixtureType == null) {
			return;
		}
		
		this.outputFile = outputFile;

		this.absAssertImpl = 
			getDecl(this.model, ClassDecl.class, 
				new DeclNamePredicate<ClassDecl>("ABSAssertImpl"));
		
		if (this.absAssertImpl == null) 
			throw new IllegalArgumentException("Cannot find ABSAssertImpl");

	}
	
	public boolean hasABSUnit() {
		return testType != null;
	}
	
	/**
	 * Generates an ABS module {@link ModuleDecl} that defines the 
	 * given test suite.
	 * 
	 * @param suite
	 * @return
	 */
	public ModuleDecl generateABSUnitTests(ApetTestSuite suite) {
		
		if (verbose) {
			System.out.println("Add basic imports...");
		}

		addBasicImports(output);
		
		for (String key : suite.keySet()) {
			if (verbose) {
				System.out.println("Generating test suite for "+key+"...");
			}
			generateABSUnitTest(suite.get(key), key);
		}
		
		Set<String> dn = new HashSet<String>();
		for (Decl d : output.getDeclList()) {
			if (d instanceof DeltaDecl) {
				dn.add(d.getName());
			}
		}
		
		if (! dn.isEmpty()) {
			if (verbose) {
				System.out.println("Generating product line description...");
			}

			ProductLine productline = new ProductLine();
			productline.setName(CONFIGURATION_NAME);
			Feature feature = new Feature();
			feature.setName(FEATURE_NAME);
			productline.addOptionalFeature(feature);
			
			for (String d : dn) {
				DeltaClause clause = new DeltaClause();
				Deltaspec spec = new Deltaspec();
				spec.setName(d);
				clause.setDeltaspec(spec);
				clause.addFeature(feature);
				productline.addDeltaClause(clause);
			}
			
			Product product = new Product();
			product.setName(PRODUCT_NAME);
			product.addFeature(feature);
			
			output.setProductLine(productline);
			output.addProduct(product);
		}
		
		if (verbose) {
			System.out.println("Pretty printing ABSUnit tests...");
		}

		printToFile(output, outputFile);
		
		if (verbose) {
			System.out.println("Validating ABSUnit tests...");
		}
		
		validateOutput();

		if (verbose) {
			System.out.println("ABSUnit tests generation successful");
		}
		
		return output;
	}
	
	private void validateOutput() {
		Model copy = model.fullCopy();
		copy.getCompilationUnit().addModuleDecl(output.fullCopy());
		
		validateOutput(copy.fullCopy(), null);
		validateOutput(copy.fullCopy(), output.getName().concat(".").concat(PRODUCT_NAME));
	}
	
	private void validateOutput(Model model, String product) {
		Model copy = model.fullCopy();
		if (product != null) {
            try {
				copy.flattenForProduct(product);
			} catch (Exception e) {
				throw new IllegalStateException("Cannot select product "+product, e);
			}
		}
		
        SemanticErrorList typeerrors = copy.typeCheck();
        for (SemanticError se : typeerrors) {
            System.err.println(se.getHelpMessage());
        }
	}
	
	private void addBasicImports(ModuleDecl module) {
		//export *;
		//import * from AbsUnit;
		//import * from AbsUnit.Hamcrest;
		//import * from AbsUnit.Hamcrest.Core;
		module.addExport(new StarExport());
		module.addImport(new StarImport("AbsUnit"));
		module.addImport(new StarImport("AbsUnit.Hamcrest"));
		module.addImport(new StarImport("AbsUnit.Hamcrest.Core"));
	}
	
	private void printToFile(ModuleDecl module, File file) {
        try {
			PrintStream stream = new PrintStream(file);
	        ABSFormatter formatter = new DefaultABSFormatter();
	        PrintWriter writer = new PrintWriter(stream, true);
	        formatter.setPrintWriter(writer);
	        module.doPrettyPrint(writer, formatter);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void generateABSUnitTest(List<TestCase> cs, String mn) {
		
		String[] sp = mn.split("\\.");
		final String methodName; 
		final String className;
		if (sp.length == 2) {
			className = sp[0];
			methodName = sp[1];
		} else if (sp.length == 1) {
			className = null;
			methodName = mn;
		} else {
			throw new IllegalArgumentException();
		}
	
		InterfaceDecl ti = createTestFixtureForClassMethodOrFunction(cs.size(), className, methodName);
		
		if (className == null) {
			createTestSuiteForFunction(cs, ti, methodName);
		} else {
			createTestSuiteForClassMethod(cs, ti, className, methodName);
		}
	}

	private Annotation getTestAnnotation(DataConstructor c) {
		return new Annotation(new DataConstructorExp(
				c.getName(), 
				new abs.frontend.ast.List<PureExp>()));
	}
	
	private RemoveMethodModifier removeRun() {
		RemoveMethodModifier modifier = 
			new RemoveMethodModifier(createMethodSig(RUN_METHOD, getUnit()));
		return modifier;
	}
	
	/**
	 * Add an add method modifier
	 * @param fieldName
	 * @param exp
	 * @param decl
	 * @return
	 */
	private AddMethodModifier addSetter(
			String fieldName, Access type) {
		MethodSig sig = new MethodSig(setterMethodName(fieldName), 
				new abs.frontend.ast.List<Annotation>(),
				getUnit(),  
				new abs.frontend.ast.List<ParamDecl>());
		
		sig.addParam(new ParamDecl("v", type, new abs.frontend.ast.List<Annotation>()));
		Block block = new Block();
		block.addStmt(getVAssign(new FieldUse(fieldName), new VarUse("v")));
		MethodImpl method = new MethodImpl(sig, block);
		AddMethodModifier modifier = new AddMethodModifier(method);
		return modifier;
	}
	
	private AddMethodModifier addGetter(String fieldName, Access returnType) {
		MethodSig sig = new MethodSig(getterMethodName(fieldName), 
				new abs.frontend.ast.List<Annotation>(),
				returnType,  
				new abs.frontend.ast.List<ParamDecl>());
		
		Block block = new Block();
		ReturnStmt rs = new ReturnStmt();
		rs.setRetExp(new FieldUse(fieldName));
		block.addStmt(rs);
		MethodImpl method = new MethodImpl(sig, block);
		AddMethodModifier modifier = new AddMethodModifier(method);
		return modifier;
	}
	
	/**
	 * Create a method signature for testing method with the given method name.
	 * 
	 * @param methodName
	 * @param decls
	 * @return
	 */
	private MethodSig createTestMethodSig(String methodName, 
			ParamDecl... decls) {
		MethodSig methodSig = createMethodSig(methodName, getUnit(), decls);
		methodSig.addAnnotation(getTestAnnotation(testType));
		return methodSig;
	}
	
	private MethodSig createMethodSig(String methodName, 
			Access returnType,
			ParamDecl... decls) {
		
		abs.frontend.ast.List<ParamDecl> dl = 
			new abs.frontend.ast.List<ParamDecl>();
		
		for (ParamDecl d : decls) {
			dl.add(d);
		}
		
		MethodSig method = new MethodSig(methodName,
				new abs.frontend.ast.List<Annotation>(),
				returnType, dl);
		
		return method;
	}
	
	private InterfaceDecl createInterface(String interfaceName) {
		return new InterfaceDecl(interfaceName, 
				new abs.frontend.ast.List<Annotation>(), 
				new abs.frontend.ast.List<InterfaceTypeUse>(), 
				new abs.frontend.ast.List<MethodSig>());
	}
	
	private InterfaceDecl createTestInterface(String interfaceName) {
		InterfaceDecl ti = createInterface(interfaceName);
		ti.addAnnotation(getTestAnnotation(fixtureType)); 
		return ti;
	}
	
	/**
	 * Get a create an ABS interface with the given name.
	 * 
	 * @param testInterface
	 * @return
	 */
	private InterfaceDecl getOrCreateTestInterfaceDecl(String testInterface) {
		//check if we already have defined this.
		InterfaceDecl ti = getDecl(output, InterfaceDecl.class, 
				AbsASTBuilderUtil.<InterfaceDecl>namePred(testInterface));
		
		if (ti == null) {
			ti = createTestInterface(testInterface);
			output.addDecl(ti);
		}
		
		return ti;
	}
	
	private MethodImpl createTestMethodImpl(MethodSig method) {
		MethodImpl methodImpl = new MethodImpl(method.copy(), new Block());
		return methodImpl;
	}
	
	private String className(String interfaceName) {
		return interfaceName + CLASS_SUFFIX;
	}
	
	private String functionClassName(String functionName) {
		return CLASS_FUNCTION_PREFIX + functionName;
	}
	
	private ClassDecl createTestClass(InterfaceDecl inf) {
		ClassDecl ct = new ClassDecl(className(inf.getName()), 
				new abs.frontend.ast.List<Annotation>(), 
				new abs.frontend.ast.List<ParamDecl>(),
				new abs.frontend.ast.List<InterfaceTypeUse>(), 
				new Opt<InitBlock>(),
				new abs.frontend.ast.List<FieldDecl>(),
				new abs.frontend.ast.List<MethodImpl>());
		
		ct.addAnnotation(getTestAnnotation(suiteType)); 
		ct.addImplementedInterfaceUse(new InterfaceTypeUse(inf.getName()));
		
		for (MethodSig m : inf.getAllMethodSigs()) {
			ct.addMethod(createTestMethodImpl(m));
		}

		FieldDecl assertImpl = new FieldDecl();
		assertImpl.setName(ASSERT_HELPER);
		assertImpl.setAccess(absAssertImpl.getImplementedInterfaceUse(0).fullCopy());
		
		InitBlock block = new InitBlock();
		block.addStmt(getVAssign(ASSERT_HELPER, newObj(absAssertImpl, false)));
		ct.setInitBlock(block);
		
		return ct;
	}
	
	private MethodImpl findMethodImpl(ClassDecl clazz, Predicate<MethodImpl> pred) {
		for (MethodImpl m : clazz.getMethodList()) {
			if (pred.predicate(m)) {
				return m;
			}
		}
		return null;
	}
	
	private MethodSig findMethodSig(InterfaceDecl inf, Predicate<MethodSig> pred) {
		for (MethodSig m : inf.getAllMethodSigs()) {
			if (pred.predicate(m)) {
				return m;
			}
		}
		return null;
	}
	
	private Map<String, Access> getTypesFromABSData(String testName, ABSData data) {
		Map<String, Access> map = new HashMap<String, Access>();
		if (data instanceof ABSRef) {
			String type = getABSDataType(data);
			String value = getABSDataValue(data);
			map.put(heapReferenceForTest(testName, value), 
					new InterfaceTypeUse(type));
		} else if (data instanceof ABSTerm) {
			ABSTerm term = (ABSTerm) data;
			for (ABSData t : getABSTermArgs(term)) {
				map.putAll(getTypesFromABSData(testName, t));
			}
		}
		return map;
	}
	
	/**
	 * Create a test suite for testing a function.
	 * 
	 * @param testCases
	 * @param testInterface
	 * @param className
	 * @param functionName
	 */
	private void createTestSuiteForFunction(
			List<TestCase> testCases,
			InterfaceDecl testInterface, 
			String functionName) {
	
		//create test class ([Suite])
		final ClassDecl testClass = createTestClass(testInterface);

		//find function under test
		FunctionDecl functionUnderTest = getDecl(model, FunctionDecl.class,
				new DeclNamePredicate<FunctionDecl>(functionName));

		final Access access = functionUnderTest.getTypeUse();

		output.addImport(generateImportAST(functionUnderTest));
			
		/*
		 * Test methods and Test cases are ordered that is,
		 * test case 1 is implemented by test method 1 and so on...
		 */
		for (int i=0; i<testCases.size(); i++) {
			TestCase testCase = testCases.get(i);

			//initial arg
			List<ABSData> inputArguments = getInputArgs(testCase);
			MethodImpl method = testClass.getMethod(i);
			String testName = method.getMethodSig().getName();
			Block block = method.getBlock();
			
			//TODO initial states' heap contains more than one object.
			Map<String,Access> typesOfObjectInHeap = new HashMap<String, Access>();
			for (ABSData arg : inputArguments) {
				typesOfObjectInHeap.putAll(getTypesFromABSData(testName, arg));
			}
			
			createObjectsInHeap(typesOfObjectInHeap, 
					testClass, getInitialState(testCase), 
					method.getMethodSig().getName(), block);
			
			//test execution
			FnApp test = makeTestExecutionForFunction(functionName, inputArguments);
			
			if (access instanceof DataTypeUse &&
				((DataTypeUse) access).getName().equals("Unit")) {
				block.addStmt(getExpStmt(test)); //no return value
				assert getReturnData(testCase) == null;
			} else {
				block.addStmt(getVarDecl("returnValue", (Access) access.fullCopy(), test));
			}
			
			//check return value
			ABSData rd = getReturnData(testCase);
			if (rd != null) {
				makeOracle("returnValue", access, rd, block);
			}
			
			//check return value
			Map<ABSRef,ABSObject> af = getAfterState(testCase);
			assert af.size() == 1;
			for (ABSRef r : af.keySet()) {
				makeGetAndAssertStatements(testName, r, af.get(r), block);
			}
		}
		
		output.addDecl(testClass);
	}
	
	private String heapReferenceForTest(String testName, String ref) {
		return ref.toLowerCase() + StringUtils.capitalize(testName);
	}
	
	/**
	 * Create a test suite for testing a method.
	 * 
	 * @param testCases
	 * @param testInterface
	 * @param className
	 * @param methodName
	 */
	private void createTestSuiteForClassMethod(
			List<TestCase> testCases,
			InterfaceDecl testInterface, 
			String className,
			String methodName) {
	
		//create test class ([Suite])
		final ClassDecl testClass = createTestClass(testInterface);

		//find class under test.
		ClassDecl classUnderTest = getDecl(model, ClassDecl.class, 
				new DeclNamePredicate<ClassDecl>(className));

		assert classUnderTest != null : 
			"It should not be possible to not " +
			"find class under test";

		//find method under test.
		MethodImpl methodUnderTest = 
				findMethodImpl(classUnderTest, new MethodNamePredicate(methodName));

		assert methodUnderTest != null : 
			"It should not be possible to not " +
			"find method under test";

		//find interface of class under test.
		InterfaceDecl interfaceOfClassUnderTest = null;
		for (InterfaceTypeUse iu : classUnderTest.getImplementedInterfaceUseList()) {
			InterfaceDecl infTemp = getDecl(model, InterfaceDecl.class, 
					new DeclNamePredicate<InterfaceDecl>(iu.getName()));

			if (findMethodSig(infTemp, new MethodSigNamePredicate(methodName)) != null) {
				interfaceOfClassUnderTest = infTemp;
				break;
			}
		}

		//return type
		MethodSig signature = methodUnderTest.getMethodSig();
		final Access access = signature.getReturnType();

		//add imports of class/interface under test
		output.addImport(generateImportAST(classUnderTest));
		output.addImport(generateImportAST(interfaceOfClassUnderTest));


		/*
		 * Test methods and Test cases are ordered that is,
		 * test case 1 is implemented by test method 1 and so on...
		 */
		for (int i=0; i<testCases.size(); i++) {
			
			if (verbose) {
				System.out.println("Generating test case "+i+"...");
			}

			TestCase testCase = testCases.get(i);

			//initial arg
			List<ABSData> inputArguments = getInputArgs(testCase);
			MethodImpl method = testClass.getMethod(i);
			String testName = method.getMethodSig().getName();
			Block block = method.getBlock();
			
			Map<String,Access> objectsTypes = new HashMap<String, Access>();
			for (ABSData d : inputArguments) {
				objectsTypes.putAll(getTypesFromABSData(testName, d));
			}
			
			createObjectsInHeap(objectsTypes, testClass, getInitialState(testCase), testName, block);
			
			//test execution
			Call test = makeTestExecutionForMethod(methodName, inputArguments);
			
			if (access instanceof DataTypeUse &&
				((DataTypeUse) access).getName().equals("Unit")) {
				block.addStmt(getExpStmt(test)); //no return value
				assert getReturnData(testCase) == null;
			} else {
				block.addStmt(getVarDecl("returnValue", access, test));
			}
			
			//check return value
			ABSData rd = getReturnData(testCase);
			if (rd != null) {
				makeOracle("returnValue", access, rd, block);
			}
			
			//check return value (using deltas)
			makeGetAndAssertStatements(testClass, getAfterState(testCase), testName, block);
		}
		
		output.addDecl(testClass);
	}
	
	private DeltaDecl getOrCreateDeltaFor(String testClassName) {
		String deltaName = deltaOnClass(testClassName);
		DeltaDecl delta = getDecl(output, DeltaDecl.class, 
				new DeclNamePredicate<DeltaDecl>(deltaName));
			
		if (delta == null) {
			delta = new DeltaDecl();
			delta.setName(deltaName);
			output.addDecl(delta);
		}
		
		return delta;
	}
	
	private void makeGetAndAssertStatements(ClassDecl testClass, 
			Map<ABSRef,ABSObject> finalHeap, 
			String testMethodName, 
			Block testMethodBlock) {
		
		String testClassName = testClass.getName();
		DeltaDecl delta = getOrCreateDeltaFor(testClassName);
		
		ModifyClassModifier modifier =
				findClassOrIfaceModifier(delta, ModifyClassModifier.class, 
						new ModifyClassModifierNamePredicate(testClassName));
		
		if (modifier == null) {
			modifier = new ModifyClassModifier();
			modifier.setName(testClassName);
			delta.addClassOrIfaceModifier(modifier);
		}
		
		String assertMethodForTest =
				assertTestMethodName(testMethodName);
		
		MethodSig sig = new MethodSig();
		sig.setName(assertMethodForTest);
		sig.setReturnType(getUnit());
		
		//add an empty method to be modified
		MethodImpl assertMethodForObjectImpl = new MethodImpl(sig, new Block());
		testClass.addMethod(assertMethodForObjectImpl);
		
		ModifyMethodModifier mmm = 
				new ModifyMethodModifier(assertMethodForObjectImpl.fullCopy());
		Block modifyBlock = mmm.getMethodImpl().getBlock();
		modifier.addModifier(mmm);
		
		SyncCall call = new SyncCall();
		call.setCallee(new VarUse("this"));
		call.setMethod(sig.getName());
		testMethodBlock.addStmt(getExpStmt(call));

		for (ABSRef r : finalHeap.keySet()) {
			makeGetAndAssertStatements(testMethodName, r, finalHeap.get(r), modifyBlock);
		}
		
	}
	
	private String initialTestMethodName(String testName) {
		return INITIAL_TEST_PREFIX + StringUtils.capitalize(testName);
	}
	
	private String assertTestMethodName(String testName) {
		return ASSERT_TEST_PREFIX + StringUtils.capitalize(testName);
	}
	
	private <T extends ClassOrIfaceModifier> T findClassOrIfaceModifier(
			DeltaDecl delta, Class<T> klazz, Predicate<T> predicate) {
		
		abs.frontend.ast.List<ClassOrIfaceModifier> modifiers = 
				delta.getClassOrIfaceModifiers();
		
		for (int i=0; i<modifiers.getNumChild(); i++) {
			ClassOrIfaceModifier modifier = modifiers.getChild(i);
			if (klazz.isInstance(modifier)) {
				T obj = klazz.cast(modifier);
				if (predicate.predicate(obj)) {
					return obj;
				}
			}
		}
		return null;
	}
	
	private void createObjectsInHeap(
			Map<String,Access> objectsInHeap,
			ClassDecl testClass, 
			Map<ABSRef,ABSObject> initialHeap, 
			String testMethodName, 
			Block testMethodBlock) {
		
		String testClassName = testClass.getName();
		DeltaDecl delta = getOrCreateDeltaFor(testClassName);
		
		ModifyClassModifier modifier =
				findClassOrIfaceModifier(delta, ModifyClassModifier.class, 
						new ModifyClassModifierNamePredicate(testClassName));
		
		if (modifier == null) {
			modifier = new ModifyClassModifier();
			modifier.setName(testClassName);
			delta.addClassOrIfaceModifier(modifier);
		}
		
		String setMethodForTest =
				initialTestMethodName(testMethodName);
		
		MethodSig sig = new MethodSig();
		sig.setName(setMethodForTest);
		sig.setReturnType(getUnit());
		
		//add an empty method to be modified
		MethodImpl setMethodForObjectImpl = new MethodImpl(sig, new Block());
		testClass.addMethod(setMethodForObjectImpl);
		
		ModifyMethodModifier mmm = new ModifyMethodModifier(setMethodForObjectImpl.fullCopy());
		Block modifyBlock = mmm.getMethodImpl().getBlock();
		modifier.addModifier(mmm);
		
		testMethodBlock.addStmt(
				getExpStmt(getCall(getThis(), setMethodForTest, false)));

		for (ABSRef r : initialHeap.keySet()) {
			makeSetStatements(objectsInHeap, r, initialHeap.get(r), 
					modifyBlock, testMethodName, testClass);
		}
		
		for (String r : objectsInHeap.keySet()) {
			FieldDecl field = new FieldDecl();
			field.setName(r);
			field.setAccess(objectsInHeap.get(r));
			testClass.addField(field);
		}
		
	}
	
	private PureExp getThis() {
		return new VarUse("this");
	}
	
	private void makeOracle(String actual, Access access, ABSData data, Block block) {
		//TODO handle object comparison!
		block.addStmt(
			getExpStmt(getCall(new VarUse(ASSERT_HELPER), "assertTrue", true, 
				new VarUse(actual), createPureExpression(data))));
	}
	
	private String deltaOnClass(String className) {
		return className + DELTA_SUFFIX;
	}
	
	private String interfaceForModifyingFieldOfClass(String className) {
		return "ModifierFieldsOf"+className+"ForTest";
	}
	
	/**
	 * Add a delta that adds Getters and Setters for clazz.
	 * @param clazz
	 */
	private void updateDelta(ClassDecl clazz) {
		String className = clazz.getName();
		
		DeltaDecl dd = getDecl(output, DeltaDecl.class, 
				new DeclNamePredicate<DeltaDecl>(deltaOnClass(className)));
		
		if (dd == null) {
			dd = new DeltaDecl();
			dd.setName(deltaOnClass(className));
			
			//add Setters and Getters
			ModifyClassModifier mcm = new ModifyClassModifier();
			mcm.setName(className);

			InterfaceDecl ai = new InterfaceDecl();
			ai.setName(interfaceForModifyingFieldOfClass(className));
			mcm.addImplementedInterfaceUse(new InterfaceTypeUse(ai.getName()));
			
			for (MethodImpl m : clazz.getMethodList()) {
				if (RUN_METHOD.equals(m.getMethodSig().getName())) {
					mcm.addModifier(removeRun());
					break;
				}
			}
			
			for (FieldDecl fd : clazz.getFieldList()) {
				AddMethodModifier smm = addSetter(fd.getName(), (Access) fd.getAccess().fullCopy());
				AddMethodModifier gmm = addGetter(fd.getName(), (Access) fd.getAccess().fullCopy());
				mcm.addModifier(smm);
				mcm.addModifier(gmm);
				ai.addBody(smm.getMethodImpl().getMethodSig());
				ai.addBody(gmm.getMethodImpl().getMethodSig());
			}
			
			dd.addClassOrIfaceModifier(new AddInterfaceModifier(ai));
			dd.addClassOrIfaceModifier(mcm);
			
			output.addDecl(dd);
		}
	}
	
	private String setterMethodName(String fieldName) {
		return SETTER_PREFIX+StringUtils.capitalize(fieldName);
	}
	
	private void makeSetStatements(Map<String, Access> objectsInHeap, 
			ABSRef ref, ABSObject state, Block block, 
			String testName, ClassDecl testClass) {
		
		String rn = heapReferenceForTest(testName, getABSDataValue(ref)); 
		String concreteTypeName = getABSObjectType(state);
		
		ClassDecl concreteType = 
				getDecl(model, ClassDecl.class, 
						new DeclNamePredicate<ClassDecl>(concreteTypeName));
		
		if (concreteType == null) {
			throw new IllegalStateException("Cannot find class: "+concreteTypeName);
		}
		
		Map<String,ABSData> fields = getABSObjectFields(state);
		abs.frontend.ast.List<ParamDecl> params = concreteType.getParamList();
		PureExp[] constructorArgs = new PureExp[params.getNumChild()];
		for (int i=0; i < params.getNumChild(); i++) {
			ParamDecl param = params.getChild(i);
			String name = param.getName();
			assert fields.containsKey(name);
			ABSData d = fields.remove(name);
			objectsInHeap.putAll(getTypesFromABSData(testName, d));
			PureExp exp = createPureExpression(d);
			constructorArgs[i] = exp;
		}
		
		block.addStmt(getVAssign(rn, newObj(concreteType, false, constructorArgs)));
		
		for (String fn : fields.keySet()) {
			SyncCall syncCall = new SyncCall();
			syncCall.setCallee(new VarUse(rn));
			syncCall.setMethod(setterMethodName(fn));
			
			ABSData d = fields.get(fn);
			objectsInHeap.putAll(getTypesFromABSData(testName, d));

			PureExp exp = createPureExpression(d);
			syncCall.addParam(exp);
			block.addStmt(getExpStmt(syncCall));
		}
		
		//ADD getter and setter
		updateDelta(getDecl(model, ClassDecl.class, 
				new DeclNamePredicate<ClassDecl>(getABSObjectType(state))));
		
	}
	
	/**
	 * Adds the type import
	 * @param dataValue
	 * @return
	 */
	private PureExp createPureExpression(ABSData dataValue) {
		String type = getABSDataType(dataValue);
		String value = getABSDataValue(dataValue);

		//import type
		Decl decl = getDecl(model, Decl.class, namePred(type));
		output.addImport(generateImportAST(decl));
		
		if (dataValue instanceof ABSTerm) {
			ABSTerm term = (ABSTerm) dataValue;
			return makeDataTermValue(term, decl);
		} else if (dataValue instanceof ABSRef) {
			return new VarUse(value);
		} else {
			throw new IllegalStateException("Cannot handle ABSData that is not " +
					"either a ABSRef or a ABSTerm");
		}
	}
	
	/**
	 * Construct a pure expression that is either a primitive literal
	 * such as Int and String, or a value of a data type.
	 * 
	 * @param value
	 * @param decl 
	 * @return
	 */
	private PureExp makeDataTermValue(ABSTerm term, Decl decl) {
		if (decl instanceof TypeSynDecl) {
			return parseValue(term);
		} else if (decl instanceof DataTypeDecl) {
			if ("String".equals(decl.getName())) {
				return new StringLiteral(getABSDataValue(term));
			} else if ("Int".equals(decl.getName())) {
				return new IntLiteral(getABSDataValue(term));
			} else {
				return parseValue(term);
			}
		} else {
			throw new IllegalStateException("Cannot handle declaration type "+decl);
		}
	}
	
	private DataConstructorExp parseValue(ABSTerm term) {
		
		final DataConstructorExp result = new DataConstructorExp();
		String fn = getABSTermFunctor(term);
		result.setConstructor(fn);
		result.setParamList(new abs.frontend.ast.List<PureExp>());
		List<ABSData> vs = getABSTermArgs(term);
		for (int i=0; i<vs.size(); i++) {
			result.setParam(createPureExpression(vs.get(i)),i);
		}
		return result;
	}
	
	private String getterMethodName(String fieldName) {
		return GETTER_PREFIX + StringUtils.capitalize(fieldName);
	}
	
	private String resultOfgetterMethodName(String fieldName) {
		return getterMethodName(fieldName) + "Var";
	}
	
	private void makeGetAndAssertStatements(String testName, 
			ABSRef ref, ABSObject state, Block block) {
		String rn = heapReferenceForTest(testName, getABSDataValue(ref)); 
		
		Map<String,ABSData> fields = getABSObjectFields(state);
		
		ClassDecl clazz = 
			getDecl(model, ClassDecl.class, 
				new DeclNamePredicate<ClassDecl>(getABSObjectType(state)));
		
		abs.frontend.ast.List<FieldDecl> fieldDecls = clazz.getFieldList();
		for (int i=0; i<fieldDecls.getNumChild(); i++) {
			FieldDecl field = fieldDecls.getChild(i);
			String fn = field.getName();
			if (fields.containsKey(fn)) {
				ABSData d = fields.get(fn);
				SyncCall syncCall = new SyncCall();
				syncCall.setCallee(new VarUse(rn));
				syncCall.setMethod(getterMethodName(fn));
				block.addStmt(getVarDecl(resultOfgetterMethodName(fn), 
						(Access) field.getAccess().fullCopy(), syncCall));
				makeOracle(resultOfgetterMethodName(fn),
						(Access) field.getAccess().fullCopy(), d,block);
			}
		}
	}
	
	private FnApp makeTestExecutionForFunction(String functionName, List<ABSData> inArgs) {
		abs.frontend.ast.List<PureExp> ps = new abs.frontend.ast.List<PureExp>();
		FnApp fa = new FnApp();
		
		if (inArgs.size() == 0) {
			throw new IllegalStateException("Inputs for a method must at least have a reference");
		}
		
		fa.setName(functionName);
		fa.setParamList(ps);
		
		for (int i=0; i<inArgs.size(); i++) {
			ABSData d = inArgs.get(i);
			PureExp exp = createPureExpression(d);
			fa.setParam(exp,i);
		}
		return fa;
	}
	
	private Call makeTestExecutionForMethod(String methodName, List<ABSData> inArgs) {
		
		if (inArgs.size() == 0) {
			throw new IllegalStateException("Inputs for a method must at least have a reference");
		}
		
		ABSData r = inArgs.get(0);
		if (! (r instanceof ABSRef)) {
			throw new IllegalStateException("Inputs for a method must at least have a reference");
		}
		
		PureExp[] ps = new PureExp[inArgs.size() - 1];
		for (int i=1; i<inArgs.size(); i++) {
			ABSData d = inArgs.get(i);
			PureExp exp = createPureExpression(d);
			ps[i-1] = exp;
		}
		
		String rn = getABSDataValue(r);
		Call call = getCall(new VarUse(rn), methodName, false, ps);
		return call;
		
	}
	
	private String testInterfaceName(String className, String capMethodName) {
		return className + INTERFACE_SUFFIX + "For" + capMethodName;
	}
	
	private String testMethodName(String capMethodName, String testCaseName) {
		return METHOD_PREFIX+ capMethodName + testCaseName;
	}
	
	/**
	 * Create Test Fixture for a given Class method or a function.
	 * 
	 * @param testCaseSize
	 * @param className
	 * @param methodName
	 * @return
	 */
	private InterfaceDecl createTestFixtureForClassMethodOrFunction(int testCaseSize, String className, 
			String methodName) {
		
		String capMethodName = StringUtils.capitalize(methodName);

		if (className == null) {
			className = functionClassName(capMethodName);
		}
		
		final String testInterfaceName = testInterfaceName(className, capMethodName);
		
		//create fixture
		final InterfaceDecl testInterface = 
			getOrCreateTestInterfaceDecl(testInterfaceName);
		
		for (int i=1; i<=testCaseSize; i++) {
			testInterface.addBody(createTestMethodSig(testMethodName(capMethodName, 
					Integer.valueOf(i).toString())));
		}
		
		return testInterface;
	}
	
	private void gatherABSUnitAnnotations() {
		for (Decl decl : this.model.getDecls()) {
			if (decl instanceof ParametricDataTypeDecl) {
				if (decl.getType().getQualifiedName().equals(test)) {
					testType = ((ParametricDataTypeDecl) decl)
							.getDataConstructor(0);
				} else if (decl.getType().getQualifiedName().equals(fixture)) {
					fixtureType = ((ParametricDataTypeDecl) decl)
							.getDataConstructor(0);
				} else if (decl.getType().getQualifiedName().equals(suite)) {
					suiteType = ((ParametricDataTypeDecl) decl)
							.getDataConstructor(0);
				} else if (decl.getType().getQualifiedName().equals(dataPoint)) {
					dataPointType = ((ParametricDataTypeDecl) decl)
							.getDataConstructor(0);
				} else if (decl.getType().getQualifiedName().equals(ignore)) {
					ignoreType = ((ParametricDataTypeDecl) decl)
							.getDataConstructor(0);
				}
			}
		}
	}

}