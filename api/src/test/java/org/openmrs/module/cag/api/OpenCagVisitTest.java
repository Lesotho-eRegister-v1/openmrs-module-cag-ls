package org.openmrs.module.cag.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;

import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.cag.Cag;
import org.openmrs.module.cag.cag.CagEncounter;
import org.openmrs.module.cag.cag.CagVisit;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * POST cagVisit sends each member's Visit with its REG encounter nested inside. The REST layer
 * fills Visit.encounters but never Encounter.visit, and visit_id is owned by the encounter, so on
 * the live server all three REG encounters of CAG visit c616721e were stored with visit_id = NULL.
 */
public class OpenCagVisitTest extends BaseModuleContextSensitiveTest {
	
	@Test
	public void openCagVisit_shouldAttachNestedEncountersToTheirVisits() {
		CagService service = Context.getService(CagService.class);
		Cag cag = new Cag();
		cag.setName("CAG");
		service.saveCag(cag);
		
		Patient attender = Context.getPatientService().getPatient(2);
		Patient buddy = Context.getPatientService().getPatient(6);
		Date started = new Date();
		
		CagVisit cagVisit = new CagVisit();
		cagVisit.setCag(cag);
		cagVisit.setAttender(attender);
		cagVisit.setDateStarted(started);
		cagVisit.setLocationName("Unknown Location");
		cagVisit.getVisits().add(visitWithNestedEncounter(attender, started));
		cagVisit.getVisits().add(visitWithNestedEncounter(buddy, started));
		
		CagVisit saved = service.openCagVisit(cagVisit);
		Context.flushSession();
		Context.clearSession();
		
		CagVisit reloaded = service.getCagVisitByUuid(saved.getUuid());
		assertEquals(2, reloaded.getVisits().size());
		for (Visit visit : reloaded.getVisits()) {
			assertEquals("REG encounter linked to visit of patient " + visit.getPatient().getId(), 1, Context
			        .getEncounterService().getEncountersByVisit(visit, false).size());
		}
		
		CagEncounter cagEncounter = service.searchCagEncounters(cag.getUuid()).get(0);
		assertEquals(2, cagEncounter.getEncounters().size());
		for (Encounter e : cagEncounter.getEncounters()) {
			assertNotNull(e.getVisit());
		}
	}
	
	@Test
	public void openCagVisit_shouldAcceptAVisitWithoutEncounters() {
		CagService service = Context.getService(CagService.class);
		Cag cag = new Cag();
		cag.setName("CAG");
		service.saveCag(cag);
		Patient attender = Context.getPatientService().getPatient(2);
		Date started = new Date();
		
		CagVisit cagVisit = new CagVisit();
		cagVisit.setCag(cag);
		cagVisit.setAttender(attender);
		cagVisit.setDateStarted(started);
		cagVisit.setLocationName("Unknown Location");
		Visit bare = new Visit(attender, Context.getVisitService().getVisitType(1), started);
		bare.setLocation(Context.getLocationService().getLocation(1));
		cagVisit.getVisits().add(bare);
		
		assertNotNull(service.openCagVisit(cagVisit));
	}
	
	@Test
	public void searchCagByMemberUuid_shouldReturnTheMemberList() {
		CagService service = Context.getService(CagService.class);
		Cag cag = new Cag();
		cag.setName("CAG");
		cag.setCagPatientList(java.util.Collections.singletonList(Context.getPatientService().getPatient(2)));
		service.saveCag(cag);
		Context.flushSession();
		
		Cag found = service.searchCagByMemberUuid(Context.getPatientService().getPatient(2).getUuid());
		assertNotNull(found.getCagPatientList());
		assertEquals(1, found.getCagPatientList().size());
	}
	
	/** Shape produced by the REST converter for cagService.startVisit: encounter.visit left null. */
	private Visit visitWithNestedEncounter(Patient patient, Date started) {
		Visit visit = new Visit(patient, Context.getVisitService().getVisitType(1), started);
		visit.setLocation(Context.getLocationService().getLocation(1));
		Encounter reg = new Encounter();
		reg.setPatient(patient);
		reg.setEncounterType(Context.getEncounterService().getEncounterType(1));
		reg.setEncounterDatetime(started);
		reg.setLocation(visit.getLocation());
		visit.getEncounters().add(reg);
		return visit;
	}
}
