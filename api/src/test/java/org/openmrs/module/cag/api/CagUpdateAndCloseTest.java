package org.openmrs.module.cag.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.cag.Cag;
import org.openmrs.module.cag.cag.CagVisit;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * Service behaviour behind the CAG REST update / close-visit calls, which used to answer 500:
 * unknown uuids threw NullPointerExceptions, every close failure was reported as a date parse
 * error, and the CAG update returned stale values.
 */
public class CagUpdateAndCloseTest extends BaseModuleContextSensitiveTest {
	
	private static final String CAG_UUID = "64feb3c1-601e-4ebb-9b55-265939125843";
	
	private static final String CAG_VISIT_UUID = "c616721e-ede2-4e79-8c7d-cac3745fb033";
	
	private static final String UNKNOWN_UUID = "00000000-0000-4000-8000-000000000000";
	
	private CagService service;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("CagPropagationTestDataset.xml");
		service = Context.getService(CagService.class);
	}
	
	@Test
	public void updateCag_shouldReturnTheNewValuesAndKeepTheMembers() {
		Cag change = new Cag();
		change.setUuid(CAG_UUID);
		change.setName("Renamed CAG");
		change.setDescription("New description");
		change.setVillage("Ha Village");
		change.setConstituency("Constituency");
		change.setDistrict("Maseru");
		
		Cag updated = service.updateCag(change);
		
		assertEquals("Renamed CAG", updated.getName());
		assertEquals("Maseru", updated.getDistrict());
		assertNotNull(updated.getDateChanged());
		assertEquals(3, updated.getCagPatientList().size());
		
		Context.flushSession();
		Context.clearSession();
		Cag reloaded = service.getCagByUuid(CAG_UUID);
		assertEquals("Renamed CAG", reloaded.getName());
		assertEquals("New description", reloaded.getDescription());
	}
	
	@Test
	public void unknownUuids_shouldGiveNullNotAnException() {
		Cag change = new Cag();
		change.setUuid(UNKNOWN_UUID);
		change.setName("x");
		
		assertNull(service.updateCag(change));
		assertNull(service.getCagByUuid(UNKNOWN_UUID));
		assertNull(service.getCagVisitByUuid(UNKNOWN_UUID));
	}
	
	@Test
	public void closeCagVisit_shouldCloseTheCagVisitAndThePresentMembersVisits() throws Exception {
		CagVisit closed = service.closeCagVisit(CAG_VISIT_UUID, "2026-11-09 19:00:00");
		
		assertNotNull(closed);
		Context.flushSession();
		Context.clearSession();
		CagVisit reloaded = service.getCagVisitByUuid(CAG_VISIT_UUID);
		assertFalse(reloaded.getIsActive());
		assertEquals(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-11-09 19:00:00"), reloaded.getDateStopped());
		Visit member = Context.getVisitService().getVisit(102);
		assertNotNull("present member's visit closed", member.getStopDatetime());
	}
	
	@Test
	public void closeCagVisit_unknownUuid_shouldGiveNull() throws Exception {
		assertNull(service.closeCagVisit(UNKNOWN_UUID, "2026-11-09 19:00:00"));
	}
	
	@Test(expected = ParseException.class)
	public void closeCagVisit_badDate_shouldBeAParseError() throws Exception {
		service.closeCagVisit(CAG_VISIT_UUID, "2026-11-09T19:00:00.000Z");
	}
	
	@Test
	public void closeCagVisit_badDate_shouldLeaveTheVisitOpen() {
		try {
			service.closeCagVisit(CAG_VISIT_UUID, "not a date");
		}
		catch (ParseException expected) {}
		assertTrue(service.getCagVisitByUuid(CAG_VISIT_UUID).getIsActive());
	}
}
