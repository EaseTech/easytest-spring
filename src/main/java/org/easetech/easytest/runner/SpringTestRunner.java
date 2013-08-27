/*
 * Copyright 2013 anuj.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.easetech.easytest.runner;

import org.easetech.easytest.annotation.Param;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.easetech.easytest.annotation.Converters;
import org.easetech.easytest.annotation.DataLoader;
import org.easetech.easytest.annotation.Intercept;
import org.easetech.easytest.annotation.Parallel;
import org.easetech.easytest.converter.Converter;
import org.easetech.easytest.converter.ConverterManager;
import org.easetech.easytest.exceptions.ParamAssertionError;
import org.easetech.easytest.interceptor.InternalSpringInterceptor;
import org.easetech.easytest.interceptor.MethodIntercepter;
import org.easetech.easytest.loader.DataConverter;
import org.easetech.easytest.loader.DataLoaderUtil;
import org.easetech.easytest.reports.data.ReportDataContainer;
import org.easetech.easytest.reports.data.TestResultBean;
import org.easetech.easytest.strategy.SchedulerStrategy;
import org.easetech.easytest.util.DataContext;
import org.easetech.easytest.util.RunAftersWithOutputData;
import org.easetech.easytest.util.TestInfo;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A {@link SpringJUnit4ClassRunner} Runner extension that adds support of input parameters as part of the {@link Test}
 * annotation. This {@link SpringJUnit4ClassRunner} extension is modified for providing convenient Data Driven Testing
 * support to its users. This Runner is capable of generating new instances of {@link FrameworkMethod} based on the test
 * data for a given method. For eg. If there is a method "testMethod(String testData)" that has three sets of test data
 * : [{"testData1"},{"testData2"},{"testData3"}], then this runner will generate three {@link FrameworkMethod} instances
 * with the method names :<br>
 * testMethod{testData1}<br>
 * testMethod{testData2}<br>
 * and<br>
 * testMethod{testData3}<br>
 * <br>
 * 
 * <br>
 * <B> A user can specify the test data at the class level, using the {@link DataLoader} annotation and override it at
 * the method level. The Runner will take care of executing the test method with the right test data.</B><br>
 * This is extremely beneficial in cases, where the user just wants to load the data once and then reuse it for all the
 * test methods. If the user wants, then he can always override the test data at the method level by specifying the
 * {@link DataLoader} annotation at the method level. <br>
 * <br>
 * In addition, this runner also introduces a new way for the user to specify the test data using {@link DataLoader}
 * annotation.
 * 
 * <br>
 * <br>
 * There is also a {@link Param} annotation to handle boiler plate tasks on behalf of the user as well as supports
 * additional functionality that eases the life of the user. For eg. it supports Java PropertyEditors to automatically
 * convert a String to the specified Object. It also supports passing a Map to the test method that contains all the
 * available test data key / value pairs for easy consumption by the user. It also supports user defined custom Objects
 * as parameters.<br>
 * <br>
 * 
 * @author Anuj Kumar
 */
public class SpringTestRunner extends SpringJUnit4ClassRunner {

    /**
     * An instance of {@link Map} that contains the data to be written to the File
     */
    private Map<String, List<Map<String, Object>>> writableData = new HashMap<String, List<Map<String, Object>>>();

    /**
     * The report container which holds all the reporting data
     */
    private final ReportDataContainer testReportContainer;

    /**
     * An instance of logger associated with the test framework.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(OldSpringTestRunner.class);

    /**
     * Convenient class member to get the list of {@link FrameworkMethod} that this runner will execute.
     */
    private final List<FrameworkMethod> frameworkMethods;

    /**
     * 
     * Construct a new NewSpringTestRunner
     * @param clazz
     * @throws InitializationError
     */
    public SpringTestRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
        Class<?> testClass = getTestClass().getJavaClass();
        setSchedulingStrategy(testClass);
        loadBeanConfiguration(testClass);
        loadClassLevelData(testClass);
        registerConverter(testClass.getAnnotation(org.easetech.easytest.annotation.Converters.class));

        try {
            // initialize report container class
            // TODO add condition whether reports must be switched on or off
            testReportContainer = new ReportDataContainer(getTestClass().getJavaClass());
            frameworkMethods = computeMethodsForTest();

        } catch (Exception e) {
            LOG.error("Exception occured while instantiating the EasyTestRunner. Exception is : ", e);
            throw new RuntimeException(e);
        }

    }

    /**
     * Overridden the compute test method to make it save the method list as class instance, so that the method does not
     * run multiple times. Also, this method now is responsible for creating multiple {@link FrameworkMethod} instances
     * for a given method with multiple test data. So, if a given test method needs to run three times with three set of
     * input test data, then this method will actually create three instances of {@link FrameworkMethod}. In order to
     * allow the user to override the default name, {@link FrameworkMethod} is extended with {@link EasyFrameworkMethod}
     * and {@link EasyFrameworkMethod#setName(String)} method introduced.
     * 
     * @return list of {@link FrameworkMethod}
     */

    protected List<FrameworkMethod> computeMethodsForTest() {

        List<FrameworkMethod> finalList = new ArrayList<FrameworkMethod>();
        // Iterator<FrameworkMethod> testMethodsItr = super.computeTestMethods().iterator();
        Class<?> testClass = getTestClass().getJavaClass();

        List<FrameworkMethod> availableMethods = getTestClass().getAnnotatedMethods(Test.class);
        List<FrameworkMethod> methodsWithNoData = new ArrayList<FrameworkMethod>();
        List<FrameworkMethod> methodsWithData = new ArrayList<FrameworkMethod>();

        for (FrameworkMethod method : availableMethods) {
            // Try loading the data if any at the method level
            if (method.getAnnotation(DataLoader.class) != null) {
                DataLoaderUtil.loadData(null, method, getTestClass(), writableData);
                methodsWithData.add(method);
            } else {
                // Method does not have its own dataloader annotation
                // Does method need input data ??
                if (method.getMethod().getParameterTypes().length == 0) {
                    methodsWithNoData.add(method);
                } else {
                    // Does method have data already loaded?
                    boolean methodDataLoaded = DataLoaderUtil.isMethodDataLoaded(DataConverter
                        .getFullyQualifiedTestName(method.getName(), testClass));
                    if (methodDataLoaded) {
                        methodsWithData.add(method);
                    } else {
                        methodsWithNoData.add(method);
                    }
                }
            }
            // Next Try registering the converters, if any at the method level
            registerConverter(method.getAnnotation(Converters.class));

        }

        for (FrameworkMethod methodWithData : methodsWithData) {
            String superMethodName = DataConverter.getFullyQualifiedTestName(methodWithData.getName(), testClass);
            for (FrameworkMethod method : availableMethods) {
                if (superMethodName.equals(DataConverter.getFullyQualifiedTestName(method.getName(), testClass))) {
                    // Load the data,if any, at the method level
                    List<Map<String, Object>> methodData = null;
                    if (DataContext.getData() != null) {
                        methodData = DataContext.getData().get(superMethodName);
                    }
                    if (methodData == null) {
                        Assert.fail("Method with name : " + superMethodName
                            + " expects some input test data. But there doesnt seem to be any test "
                            + "data for the given method. Please check the Test Data file for the method data. "
                            + "Possible cause could be that the data did not get loaded at all from the file "
                            + "or a spelling mismatch in the method name. Check logs for more details.");
                    }
                    for (Map<String, Object> testData : methodData) {
                        TestResultBean testResultBean = new TestResultBean(methodWithData.getMethod().getName(),
                            new Date());
                        testReportContainer.addTestResult(testResultBean);
                        // Create a new FrameworkMethod for each set of test data
                        EasyFrameworkMethod easyMethod = new EasyFrameworkMethod(method.getMethod(), testData,
                            testResultBean);
                        easyMethod.setName(method.getName().concat(testData.toString()));
                        finalList.add(easyMethod);
                    }

                    // Since the runner only ever handles a single method, we break out of the loop as soon as we
                    // have
                    // found our method.
                    break;
                }
            }
        }
        for (FrameworkMethod fMethod : methodsWithNoData) {
            TestResultBean testResultBean = new TestResultBean(fMethod.getMethod().getName(), new Date());
            testReportContainer.addTestResult(testResultBean);
            EasyFrameworkMethod easyMethod = new EasyFrameworkMethod(fMethod.getMethod(), null, testResultBean);
            finalList.add(easyMethod);
        }
        if (finalList.isEmpty()) {
            Assert.fail("No method exists for the Test Runner");
        }
        return finalList;
    }

    /**
     * Set whether the tests should be run in parallel or serial.
     */
    protected void setSchedulingStrategy(Class<?> testClass) {
        if (testClass.getAnnotation(Parallel.class) != null) {
            super.setScheduler(SchedulerStrategy.getScheduler(testClass));
        }
    }

    /**
     * @see TestConfigUtil#loadTestBeanConfig(Class)
     */
    protected void loadBeanConfiguration(Class<?> testClass) {
        TestConfigUtil.loadTestBeanConfig(testClass);
    }

    /**
     * Load any class level test data
     * 
     * @see DataLoaderUtil#loadData(Class, FrameworkMethod, org.junit.runners.model.TestClass, Map)
     * @param klass
     */
    protected void loadClassLevelData(Class<?> testClass) {
        DataLoaderUtil.loadData(testClass, null, getTestClass(), writableData);
    }

    /**
     * Method responsible for registering the converters with the EasyTest framework
     * 
     * @param converter the annotation {@link Converters}
     */
    @SuppressWarnings("rawtypes")
    public void registerConverter(Converters converter) {
        if (converter != null) {
            Class<? extends Converter>[] convertersToRegister = converter.value();
            if (convertersToRegister != null && convertersToRegister.length != 0) {
                for (Class<? extends Converter> value : convertersToRegister) {
                    ConverterManager.registerConverter(value);
                }
            }
        }

    }

    /**
     * Get the instance of the class under test
     * 
     * @return the instance of class under test
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    protected Object getTestInstance() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
        InvocationTargetException {
        return getTestClass().getOnlyConstructor().newInstance();
    }

    /**
     * Compute any test methods
     * 
     * @return a list of {@link FrameworkMethod}s
     */
    protected List<FrameworkMethod> computeTestMethods() {
        return frameworkMethods;
    }

    /**
     * Override the filter method from {@link ParentRunner} so that individual tests can be run using EasyTest
     * 
     * @param filter
     * @throws NoTestsRemainException
     */
    public void filter(Filter filter) throws NoTestsRemainException {

        for (Iterator<FrameworkMethod> iter = frameworkMethods.iterator(); iter.hasNext();) {
            FrameworkMethod each = iter.next();
            if (shouldRun(filter, each))
                try {
                    filter.apply(each);
                } catch (NoTestsRemainException e) {
                    iter.remove();
                }
            else
                iter.remove();
        }
        if (frameworkMethods.isEmpty()) {
            throw new NoTestsRemainException();
        }
    }

    private boolean shouldRun(Filter filter, FrameworkMethod each) {
        return filter.shouldRun(describeFiltarableChild(each));
    }

    private Description describeFiltarableChild(FrameworkMethod each) {
        return Description.createTestDescription(getTestClass().getJavaClass(), each.getMethod().getName(),
            each.getAnnotations());
    }

    /**
     * Instrument the class's field that are marked with {@link Intercept} annotation
     * 
     * @param testClass the class under test
     * @throws IllegalArgumentException if an exception occurred
     * @throws IllegalAccessException if an exception occurred
     * @throws AopConfigException if an exception occurred
     * @throws InstantiationException if an exception occurred
     */
    protected void instrumentClass(Class<?> testClass, Object testInstance) throws IllegalArgumentException,
        IllegalAccessException, AopConfigException, InstantiationException {
        Field[] fields = testClass.getDeclaredFields();
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Intercept interceptor = field.getAnnotation(Intercept.class);
                if (interceptor != null) {
                    Class<? extends MethodIntercepter> interceptorClass = interceptor.interceptor();
                    // This is the field we want to enhance
                    Object fieldInstance = field.get(testInstance);
                    ProxyFactory factory = new ProxyFactory();
                    factory.setTarget(fieldInstance);
                    InternalSpringInterceptor internalIntercepter = new InternalSpringInterceptor();
                    internalIntercepter.setUserIntercepter(interceptorClass.newInstance());
                    factory.addAdvice(internalIntercepter);
                    Object proxy = factory.getProxy();
                    field.set(testInstance, proxy);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Override the name of the test. In case of EasyTest, it will be the name of the test method concatenated with the
     * input test data that the method will run with.
     * 
     * @param method the {@link FrameworkMethod}
     * @return an overridden test method Name
     */

    protected String testName(final FrameworkMethod method) {
        return String.format("%s", method.getName());
    }

    /**
     * Validate that there could ever be only one constructor.
     * 
     * @param errors list of any errors while validating the Constructor
     */

    protected void validateConstructor(List<Throwable> errors) {
        validateOnlyOneConstructor(errors);
    }

    /**
     * Adds to {@code errors} for each method annotated with {@code @Test}, {@code @Before}, or {@code @After} that is
     * not a public, void instance method with no arguments.
     * 
     * @deprecated unused API, will go away in future version
     */
    @Deprecated
    protected void validateInstanceMethods(List<Throwable> errors) {
        validatePublicVoidNoArgMethods(After.class, false, errors);
        validatePublicVoidNoArgMethods(Before.class, false, errors);
        validateTestMethods(errors);

        if (getTestClass().getAnnotatedMethods(Test.class).size() == 0)
            errors.add(new Exception("No runnable methods"));
    }

    /**
     * Validate the test methods.
     * 
     * @param errors list of any errors while validating test method
     */

    protected void validateTestMethods(List<Throwable> errors) {
        // Do Nothing as we now support public non void arg test methods
    }

    protected Statement methodBlock(FrameworkMethod method) {
        return withTestResult((EasyFrameworkMethod) method, super.methodBlock(method));
    }

    protected Statement withTestResult(final EasyFrameworkMethod method, final Statement statement) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                TestResultBean testResult = method.getTestResult();
                try {
                    statement.evaluate();
                    testResult.setPassed(Boolean.TRUE);
                } catch (Throwable e) {

                    if (e instanceof AssertionError) { // Assertion error
                        testResult.setPassed(Boolean.FALSE);
                        testResult.setResult(e.getMessage());
                        throw new ParamAssertionError(e, method.getName());

                    } else { // Exception
                        testResult.setException(Boolean.TRUE);
                        testResult.setExceptionResult(e.toString());
                        throw e;

                    }

                }

            }
        };
    }

    /**
     * @see TestConfigUtil#loadTestConfigurations(Class, Object)
     */
    protected void loadTestConfigurations(Object testInstance) {
        TestConfigUtil.loadTestConfigurations(getTestClass().getJavaClass(), testInstance);
    }

    /**
     * @see TestConfigUtil#loadResourceProperties
     */
    protected void loadResourceProperties(Object testInstance) {
        TestConfigUtil.loadResourceProperties(getTestClass().getJavaClass(), testInstance);
    }

    /**
     * Returns a new fixture for running a test. Default implementation executes the test class's no-argument
     * constructor (validation should have ensured one exists).
     */
    protected Object createTest() throws Exception {
        Object testInstance = getTestClass().getOnlyConstructor().newInstance();

        loadTestConfigurations(testInstance);
        loadResourceProperties(testInstance);
        getTestContextManager().prepareTestInstance(testInstance);
        instrumentClass(getTestClass().getJavaClass(), testInstance);

        return testInstance;

    }

    /**
     * Returns a {@link Statement} that invokes {@code method} on {@code test}
     */
    protected Statement methodInvoker(FrameworkMethod method, Object testInstance) {
        return new InternalParameterizedStatement((EasyFrameworkMethod) method, getTestClass(), testInstance);
    }

    /**
     * Returns a {@link Statement}: run all non-overridden {@code @AfterClass} methods on this class and superclasses
     * before executing {@code statement}; all AfterClass methods are always executed: exceptions thrown by previous
     * steps are combined, if necessary, with exceptions from AfterClass methods into a {@link MultipleFailureException}
     * .
     * 
     * This method is also responsible for writing the data to the output file in case the user is returning test data
     * from the test method. This method will make sure that the data is written to the output file once after the
     * Runner has completed and not for every instance of the test method.
     */

    protected Statement withAfterClasses(Statement statement) {
        List<FrameworkMethod> afters = getTestClass().getAnnotatedMethods(AfterClass.class);
        List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
        List<TestInfo> testInfoList = new ArrayList<TestInfo>();

        // populateTestInfo(testInfo);
        // THere would always be atleast one method associated with the Runner, else validation would fail.
        for (FrameworkMethod method : testMethods) {
            TestInfo testInfo = null;

            // Only if the return type of the Method is not VOID, we try to determine the right loader and data
            // files.
            DataLoader loaderAnnotation = method.getAnnotation(DataLoader.class);
            if (loaderAnnotation != null) {
                testInfo = DataLoaderUtil.determineLoader(loaderAnnotation, getTestClass());

            } else {
                loaderAnnotation = getTestClass().getJavaClass().getAnnotation(DataLoader.class);
                if (loaderAnnotation != null) {
                    testInfo = DataLoaderUtil.determineLoader(loaderAnnotation, getTestClass());
                }
            }
            if (testInfo != null) {
                testInfo.setMethodName(method.getName());
                testInfoList.add(testInfo);
            }

        }
        RunAftersWithOutputData runAftersWithOutputData = new RunAftersWithOutputData(statement, afters, null,
            testInfoList, writableData, testReportContainer);
        return runAftersWithOutputData;
    }

}
