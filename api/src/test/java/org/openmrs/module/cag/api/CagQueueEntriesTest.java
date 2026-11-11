package org.openmrs.module.cag.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.cag.CagVisit;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * CAG information for the Clinical queue: which queue visits belong to an active CAG visit, and
 * which of them is the physically present member's (the CAG visit's attender).
 */
public class CagQueueEntriesTest extends BaseModuleContextSensitiveTest {
	
	private static final String CAG_UUID = "64feb3c1-601e-4ebb-9b55-265939125843";
	
	private static final String CAG_VISIT_UUID = "c616721e-ede2-4e79-8c7d-cac3745fb033";
	
	/** Attender's (patient 2) visit. */
	private static final String ATTENDER_VISIT = "b882df20-c2a0-4457-a426-a3fd2259efe3";
	
	/** Member (patient 6) visit on the same CAG visit. */
	private static final String MEMBER_VISIT = "28551d9f-761d-4989-b8d4-12ff43113df3";
	
	private CagService service;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("CagPropagationTestDataset.xml");
		service = Context.getService(CagService.class);
	}
	
	@Test
	public void shouldMarkOnlyTheAttenderAsThePresentMember() {
		String nonCagVisit = nonCagVisitUuid();
		
		Map<String, CagQueueEntry> byVisit = byVisit(service.getQueueEntries(Arrays.asList(ATTENDER_VISIT, MEMBER_VISIT,
		    nonCagVisit, "00000000-0000-4000-8000-000000000000")));
		
		assertEquals("only the requested CAG visits", 2, byVisit.size());
		Patient attender = Context.getPatientService().getPatient(2);
		String attenderName = attender.getGivenName() + " " + attender.getFamilyName();
		
		CagQueueEntry present = byVisit.get(ATTENDER_VISIT);
		assertTrue(present.isPresentMember());
		assertEquals(attender.getUuid(), present.getPatientUuid());
		
		CagQueueEntry member = byVisit.get(MEMBER_VISIT);
		assertFalse(member.isPresentMember());
		assertEquals(Context.getPatientService().getPatient(6).getUuid(), member.getPatientUuid());
		
		for (CagQueueEntry entry : byVisit.values()) {
			assertEquals(CAG_UUID, entry.getCagUuid());
			assertEquals("Test CAG", entry.getCagName());
			assertEquals(CAG_VISIT_UUID, entry.getCagVisitUuid());
			assertEquals(attender.getUuid(), entry.getPresentMemberUuid());
			assertEquals(attenderName, entry.getPresentMemberName());
		}
	}
	
	@Test
	public void closedCagVisit_shouldGiveNoEntries() {
		CagVisit cagVisit = service.getCagVisitByUuid(CAG_VISIT_UUID);
		cagVisit.setIsActive(false);
		Context.flushSession();
		
		assertTrue(service.getQueueEntries(Arrays.asList(ATTENDER_VISIT, MEMBER_VISIT)).isEmpty());
	}
	
	@Test
	public void noVisits_shouldGiveNoEntries() {
		assertTrue(service.getQueueEntries(new ArrayList<String>()).isEmpty());
		assertTrue(service.getQueueEntries(null).isEmpty());
	}
	
	private String nonCagVisitUuid() {
		Visit visit = Context.getVisitService().getVisit(1);
		assertNotNull("standard test data has visit 1", visit);
		return visit.getUuid();
	}
	
	private static Map<String, CagQueueEntry> byVisit(List<CagQueueEntry> entries) {
		Map<String, CagQueueEntry> map = new HashMap<String, CagQueueEntry>();
		for (CagQueueEntry entry : entries) {
			map.put(entry.getVisitUuid(), entry);
		}
		return map;
	}
}
