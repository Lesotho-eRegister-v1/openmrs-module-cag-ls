package org.openmrs.module.cag.advice;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.api.propagation.CagEncounterPropagator;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Hooks EncounterService.saveEncounter, which is what Bahmni Clinical's POST
 * bahmnicore/bahmniencounter ends up calling, so the CAG attender's follow-up reaches the other
 * members whichever UI saved it. Declared in omod config.xml.
 * <p>
 * The mapping runs once per encounter, just before the surrounding transaction commits, and not on
 * every saveEncounter call: emrapi's EmrEncounterServiceImpl.save switches the session to
 * FlushMode.MANUAL and saves the same encounter several times, so a lookup during those calls
 * cannot see what the previous call linked, and each call created another set of copies (live,
 * 2026-11-09). Running in beforeCommit keeps it in the same transaction: a failure still rolls the
 * attender's save back.
 */
public class CagEncounterSaveAdvice implements AfterReturningAdvice {
	
	/** Members' copies are saved through the same service; do not propagate those again. */
	private static final ThreadLocal<Boolean> PROPAGATING = new ThreadLocal<Boolean>();
	
	/** Transaction resource key; shared by every instance in case the advice is registered twice. */
	private static final Object PENDING_KEY = CagEncounterSaveAdvice.class.getName() + ".pending";
	
	@Override
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) {
		if (!"saveEncounter".equals(method.getName()) || !(returnValue instanceof Encounter)
		        || Boolean.TRUE.equals(PROPAGATING.get())) {
			return;
		}
		Encounter encounter = (Encounter) returnValue;
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			propagate(encounter);
			return;
		}
		Pending pending = (Pending) TransactionSynchronizationManager.getResource(PENDING_KEY);
		if (pending == null) {
			pending = new Pending();
			TransactionSynchronizationManager.bindResource(PENDING_KEY, pending);
			TransactionSynchronizationManager.registerSynchronization(pending);
		}
		pending.encounters.put(encounter.getUuid(), encounter);
	}
	
	private static void propagate(Encounter encounter) {
		PROPAGATING.set(Boolean.TRUE);
		try {
			Context.getRegisteredComponent("cag.encounterPropagator", CagEncounterPropagator.class).propagate(encounter);
		}
		finally {
			PROPAGATING.remove();
		}
	}
	
	private static class Pending extends TransactionSynchronizationAdapter {
		
		/** By uuid: the same encounter saved several times in one transaction is mapped once. */
		private final Map<String, Encounter> encounters = new LinkedHashMap<String, Encounter>();
		
		@Override
		public void beforeCommit(boolean readOnly) {
			List<Encounter> toMap = new ArrayList<Encounter>(encounters.values());
			encounters.clear();
			for (Encounter encounter : toMap) {
				propagate(encounter);
			}
		}
		
		@Override
		public void afterCompletion(int status) {
			TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_KEY);
		}
	}
}
