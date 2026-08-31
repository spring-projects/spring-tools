package org.test;

public class ConcatenatedPointcutExamples {

	@Pointcut("target(com.example.service.MyService)")
	public void targetService() {}

	@Around("target" + "Service()")
	public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
	    return joinPoint.proceed();
	}

}
