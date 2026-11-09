package org.openmrs.module.cag.advice;

import java.lang.reflect.Method;

import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.api.propagation.CagEncounterPropagator;
import org.springframework.aop.AfterReturningAdvice;

/**
 * Hooks EncounterService.saveEncounter, which is what Bahmni Clinical's POST
 * bahmnicore/bahmniencounter ends up calling, so the CAG attender's follow-up reaches the other
 * members whichever UI saved it. Declared in omod config.xml.
 */
public class CagEncounterSaveAdvice implements AfterReturningAdvice {
	
	/** Members' copies are saved through the same service; do not propagate those again. */
	private static final ThreadLocal<Boolean> PROPAGATING = new ThreadLocal<Boolean>();
	
	@Override
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) {
		if (!"saveEncounter".equals(method.getName()) || !(returnValue instanceof Encounter)
		        || Boolean.TRUE.equals(PROPAGATING.get())) {
			return;
		}
		PROPAGATING.set(Boolean.TRUE);
		try {
			Context.getRegisteredComponent("cag.encounterPropagator", CagEncounterPropagator.class).propagate(
			    (Encounter) returnValue);
		}
		finally {
			PROPAGATING.remove();
		}
	}
}
