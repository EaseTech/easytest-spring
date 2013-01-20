
package org.easetech.easytest.runner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.easetech.easytest.annotation.DataLoader;
import org.easetech.easytest.annotation.Intercept;
import org.easetech.easytest.annotation.Param;
import org.easetech.easytest.interceptor.InternalSpringInterceptor;
import org.easetech.easytest.interceptor.MethodIntercepter;
import org.easetech.easytest.loader.DataConverter;
import org.easetech.easytest.loader.DataLoaderUtil;
import org.easetech.easytest.reports.data.ReportDataContainer;
import org.easetech.easytest.reports.data.TestResultBean;
import org.easetech.easytest.util.DataContext;
import org.easetech.easytest.util.RunAftersWithOutputData;
import org.easetech.easytest.util.TestInfo;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A Spring based implementation of {@link Suite} that encapsulates the {@link EasyTestRunner} in order to provide users
 * with clear indication of which test method is run and what is the input test data that the method is run with. For
 * example, when a user runs the test method with name : <B><I>getTestData</I></B> with the following test data:
 * <ul>
 * <li><B>"libraryId=1 and itemId=2"</B></li>
 * <li><B>"libraryId=2456 and itemId=789"</B></li><br>
 * <br>
 * 
 * then, {@link SpringTestRunner}, will provide the details of the executing test method in the JUnit supported IDEs
 * like this:
 * 
 * <ul>
 * <li><B><I>getTestData{libraryId=1 ,itemId=2}</I></B></li>
 * <li><B><I>getTestData{libraryId=2456 ,itemId=789}</I></B></li></br></br>
 * 
 * This gives user the clear picture of which test was run with which input test data.
 * 
 * For details on the actual Runner implementation, see {@link EasyTestRunner}
 * 
 * @author Anuj Kumar
 * 
 * 
 */
public class SpringTestRunner extends BaseSuite {


    /**
     * An instance of logger associated with the test framework.
     */
    protected static final Logger PARAM_LOG = LoggerFactory.getLogger(SpringTestRunner.class);
    

    /**
     * 
     * Construct a new {@link SpringTestRunner}. During construction, we will load the test data, and then we will
     * create a list of {@link EasyTestRunner}. each instance of {@link DataDrivenTestRunner} in the list will
     * correspond to a single method in the Test Class under test.<br>
     * The algorithm is as follows:<br>
     * <ul>
     * <li>STEP 1: Load the test data. This will also do the check whether there exists a {@link DataLoader} annotation
     * at the class level</li>
     * <li>Iterate over each method.<br>
     * For each method:
     * <ol>
     * <li>If method has {@link DataLoader} annotation, it means that there is test data associated with the test
     * method.<br>
     * In such a case add the method to the methodsWithData List.
     * <li>If method does not have a {@link DataLoader} annotation, then:
     * <ol>
     * <li>Check if there already exists data for the method. This is possible as the data could have been loaded at the
     * class level.<br>
     * <li>If the data for the given method exists, add the method to the methodsWithData List.
     * <li>If the data does not exists for the given test method, put it aside in a list of unused methods,
     * </ol>
     * </ol>
     * Iteration over each method ends.<br>
     * 
     * Finally create an instance of {@link EasyTestRunner} and make it use all the different types of methods we
     * identified.<br>
     * We need to identify methods with data and methods with no data primarily to group the test methods together as
     * well as to efficiently create new test methods for each method that has test data associated with it. This whole
     * process will happen for each of the test class that is part of the Suite.
     * 
     * @param klass the test class
     * @throws InitializationError if an initializationError occurs
     */
    public SpringTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
        runners.add(new EasyTestRunner(klass));
    }


    /**
     * A {@link BlockJUnit4ClassRunner} Runner implementation that adds support of input parameters as part of the
     * {@link Test} annotation. This {@link BlockJUnit4ClassRunner} extension is modified for providing convenient Data
     * Driven Testing support to its users. This Runner is capable of generating new instances of
     * {@link FrameworkMethod} based on the test data for a given method. For eg. If there is a method
     * "testMethod(String testData)" that has three sets of test data : [{"testData1"},{"testData2"},{"testData3"}],
     * then this runner will generate three {@link FrameworkMethod} instances with the method names :<br>
     * testMethod{testData1}<br>
     * testMethod{testData2}<br>
     * and<br>
     * testMethod{testData3}<br>
     * <br>
     * 
     * <br>
     * <B> A user can specify the test data at the class level, using the {@link DataLoader} annotation and override it
     * at the method level. The Runner will take care of executing the test method with the right test data.</B><br>
     * This is extremely beneficial in cases, where the user just wants to load the data once and then reuse it for all
     * the test methods. If the user wants, then he can always override the test data at the method level by specifying
     * the {@link DataLoader} annotation at the method level. <br>
     * <br>
     * In addition, this runner also introduces a new way for the user to specify the test data using {@link DataLoader}
     * annotation.
     * 
     * <br>
     * <br>
     * There is also a {@link Param} annotation to handle boiler plate tasks on behalf of the user as well as supports
     * additional functionality that eases the life of the user. For eg. it supports Java PropertyEditors to
     * automatically convert a String to the specified Object. It also supports passing a Map to the test method that
     * contains all the available test data key / value pairs for easy consumption by the user. It also supports user
     * defined custom Objects as parameters.<br>
     * <br>
     * 
     * @author Anuj Kumar
     */
    private class EasyTestRunner extends SpringJUnit4ClassRunner {

        /**
         * Convenient class member to get the list of {@link FrameworkMethod} that this runner will execute.
         */
        List<FrameworkMethod> frameworkMethods;
        

        /**
         * The report container which holds all the reporting data
         */
        private ReportDataContainer testReportContainer = null;

        /**
         * The actual instance of the test class. This is extremely handy in cases where we want to reflectively set
         * instance fields on a test class.
         */
        Object testInstance;
        
        /**
         * An instance that contains the result for a single test execution
         */
        TestResultBean testResult;

        /**
         * 
         * Construct a new DataDrivenTestRunner
         * 
         * @param klass the test class whose test methods needs to be executed
         * @throws InitializationError if any error occurs
         */
        public EasyTestRunner(Class<?> klass) throws InitializationError {
            super(klass);
            try {
                testReportContainer = new ReportDataContainer(getTestClass().getJavaClass());
                testInstance = getTestClass().getOnlyConstructor().newInstance();
                getTestContextManager().prepareTestInstance(testInstance);
                TestConfigUtil.loadTestConfigurations(getTestClass().getJavaClass(), testInstance);
                instrumentClass(getTestClass().getJavaClass());

            } catch (Exception e) {
                Assert.fail("Test failed while trying to instrument fileds in the class : "
                    + getTestClass().getJavaClass() + " Exception is : " + e);
            }
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
        protected void instrumentClass(Class<?> testClass) throws IllegalArgumentException, IllegalAccessException,
            AopConfigException, InstantiationException {
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
         * Try to collect any initialization errors, if any.
         * 
         * @param errors
         */

        protected void collectInitializationErrors(List<Throwable> errors) {
            super.collectInitializationErrors(errors);
            // validateDataPointFields(errors);
        }

        /**
         * Override the name of the test. In case of EasyTest, it will be the name of the test method concatenated with
         * the input test data that the method will run with.
         * 
         * @param method the {@link FrameworkMethod}
         * @return an overridden test method Name
         */

        protected String testName(final FrameworkMethod method) {
            return String.format("%s", method.getName());
        }

        /**
         * Overridden the compute test method to make it save the method list as class instance, so that the method does
         * not run multiple times. Also, this method now is responsible for creating multiple {@link FrameworkMethod}
         * instances for a given method with multiple test data. So, if a given test method needs to run three times
         * with three set of input test data, then this method will actually create three instances of
         * {@link FrameworkMethod}. In order to allow the user to override the default name, {@link FrameworkMethod} is
         * extended with {@link EasyFrameworkMethod} and {@link EasyFrameworkMethod#setName(String)} method introduced.
         * 
         * @return list of {@link FrameworkMethod}
         */

        protected List<FrameworkMethod> computeTestMethods() {
            if (frameworkMethods != null && !frameworkMethods.isEmpty()) {
                return frameworkMethods;
            }
            List<FrameworkMethod> finalList = new ArrayList<FrameworkMethod>();
            // Iterator<FrameworkMethod> testMethodsItr = super.computeTestMethods().iterator();
            Class<?> testClass = getTestClass().getJavaClass();
            for (FrameworkMethod methodWithData : methodsWithData) {
                String superMethodName = DataConverter.getFullyQualifiedTestName(methodWithData.getName(), testClass);
                for (FrameworkMethod method : super.computeTestMethods()) {

                    if (superMethodName.equals(DataConverter.getFullyQualifiedTestName(method.getName(), testClass))) {
                        // Load the data,if any, at the method level
                        DataLoaderUtil.loadData(null, method, getTestClass(), writableData);
                        List<Map<String, Object>> methodData = DataContext.getData().get(superMethodName);
                        if (methodData == null) {
                            Assert.fail("Method with name : " + superMethodName
                                + " expects some input test data. But there doesnt seem to be any test "
                                + "data for the given method. Please check the Test Data file for the method data. "
                                + "Possible cause could be a spelling mismatch.");
                        }
                        for (Map<String, Object> testData : methodData) {
                            // Create a new FrameworkMethod for each set of test data
                            EasyFrameworkMethod easyMethod = new EasyFrameworkMethod(method.getMethod());
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
            finalList.addAll(methodsWithNoData);
            if (finalList.isEmpty()) {
                Assert.fail("No method exists for the Test Runner");
            }
            frameworkMethods = finalList;
            return finalList;
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
         * Validate the test methods.
         * 
         * @param errors list of any errors while validating test method
         */

        protected void validateTestMethods(List<Throwable> errors) {
            // Do Nothing as we now support public non void arg test methods
        }

        protected Object createTest() throws Exception {
            return null;

        }

        /**
         * Override the methodBlock to return custom {@link ParamAnchor}
         * 
         * @param method the Framework Method
         * @return a compiled {@link Statement} object to be evaluated
         */

        public Statement methodBlock(final FrameworkMethod method) {
            return new InternalParameterizedStatement(method, testResult, testReportContainer, writableData, getTestClass(), testInstance);
        }

        /**
         * Returns a {@link Statement}: run all non-overridden {@code @AfterClass} methods on this class and
         * superclasses before executing {@code statement}; all AfterClass methods are always executed: exceptions
         * thrown by previous steps are combined, if necessary, with exceptions from AfterClass methods into a
         * {@link MultipleFailureException}.
         * 
         * This method is also responsible for writing the data to the output file in case the user is returning test
         * data from the test method. This method will make sure that the data is written to the output file once after
         * the Runner has completed and not for every instance of the test method.
         */

        protected Statement withAfterClasses(Statement statement) {
            List<FrameworkMethod> afters = getTestClass().getAnnotatedMethods(AfterClass.class);
            List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
            List<TestInfo> testInfoList = new ArrayList<TestInfo>();
            TestInfo testInfo = null;
            // populateTestInfo(testInfo);
            // THere would always be atleast one method associated with the Runner, else validation would fail.
            for (FrameworkMethod method : testMethods) {

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
            return new RunAftersWithOutputData(statement, afters, null, testInfoList, writableData, testReportContainer);
        }

    }

}
