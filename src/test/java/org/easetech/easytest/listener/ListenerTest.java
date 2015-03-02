package org.easetech.easytest.listener;

import org.easetech.easytest.runner.SpringTestRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@RunWith(SpringTestRunner.class)
@TestExecutionListeners(listeners = {ListenerTest.class, 
		DependencyInjectionTestExecutionListener.class})
@ContextConfiguration("/test-context.xml")
public class ListenerTest implements TestExecutionListener {

	@BeforeClass
	public static void before() {
		System.out.println("BeforeClass called");
	}
	
	@Test
	public void myTest() {
		System.out.println("Test executed");
	}
	
	@AfterClass
	public static void after() {
		System.out.println("AfterClass called");
	}

	public void beforeTestClass(TestContext testContext) throws Exception {
		System.out.println("Listener before test class called");
	}

	public void prepareTestInstance(TestContext testContext) throws Exception {
		System.out.println("Prepare test instance called");
	}

	public void beforeTestMethod(TestContext testContext) throws Exception {
		System.out.println("Listener before test method called");
	}

	public void afterTestMethod(TestContext testContext) throws Exception {
		System.out.println("Listener after test method called");
	}

	public void afterTestClass(TestContext testContext) throws Exception {
		System.out.println("Listener after test class called");
	}
}
