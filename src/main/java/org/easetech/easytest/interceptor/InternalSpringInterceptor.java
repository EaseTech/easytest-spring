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
package org.easetech.easytest.interceptor;

import org.easetech.easytest.annotation.Intercept;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * A internal implementation of the {@link MethodInterceptor} implementation that hides the implementation logic
 * of intercepting the method call from the user.
 * It always has an instance of {@link MethodIntercepter} whose 
 * {@link MethodIntercepter#intercept(Method, Object, Object[])} method is called internally.
 * The actual implementation of the {@link MethodIntercepter} is provided by the user as an attribute to the {@link Intercept} annotation.
 * If none is provided, then {@link DefaultMethodIntercepter} implementation is used.
 * 
 * @author Anuj Kumar
 *
 */
public class InternalSpringInterceptor implements MethodInterceptor {
    
    /**
     * An instance of the {@link MethodIntercepter}
     */
    private MethodIntercepter userIntercepter;
    
    /**
     * An instance of logger associated with the test framework.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(InternalSpringInterceptor.class);

    /**
     * Intercept the method with the advice
     * @param invocation 
     * @return returned value
     * @throws Throwable
     */

    public Object invoke(MethodInvocation invocation) throws Throwable {
        return userIntercepter.intercept(invocation.getMethod(), invocation.getThis(), invocation.getArguments());
        
    }

    /**
     * Return an instance of {@link MethodIntercepter}. See the class javadocs for details.
     * @return the userIntercepter
     */
    public MethodIntercepter getUserIntercepter() {
        return userIntercepter;
    }

    /**
     * Set the user interceptor
     * @param userIntercepter the userIntercepter to set
     */
    public void setUserIntercepter(MethodIntercepter userIntercepter) {
        this.userIntercepter = userIntercepter;
    }
    
    



    


}
