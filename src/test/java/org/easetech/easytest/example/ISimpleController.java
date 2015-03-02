package org.easetech.easytest.example;

import org.omg.CORBA.portable.ApplicationException;

public interface ISimpleController {

	public HelloWorld getMessage(String serviceName)
	throws ApplicationException;

	public HelloWorld getMessage(Request request)
	throws ApplicationException;

}
