package server;

import java.lang.reflect.Method;

import server.interfaces.MethodContainer;

public class CinnamonMethodContainer implements MethodContainer {

	String command;
	@SuppressWarnings("unchecked")
	Class methodClass;
	Method method;
	Boolean checkTrigger;
	
	@SuppressWarnings("unchecked")
	public CinnamonMethodContainer(String command, Class methodClass, Method method, Boolean checkTrigger){
		this.command = command;
		this.methodClass = methodClass;
		this.method = method;
		this.checkTrigger = checkTrigger;
	}

	/**
	 * @return the command
	 */
	@Override
	public String getCommand() {
		return command;
	}

	/**
	 * @return the methodClass
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Class getMethodClass() {
		return methodClass;
	}

	/**
	 * @return the method
	 */
	@Override
	public Method getMethod() {
		return method;
	}

	@Override
	public Boolean checkTrigger() {
		return checkTrigger;
	}
	
}
