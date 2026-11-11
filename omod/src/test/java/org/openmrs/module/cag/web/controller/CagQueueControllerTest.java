package org.openmrs.module.cag.web.controller;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.openmrs.module.cag.api.CagQueueEntry;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;

/**
 * JSON shape the Clinical queue (cagQueueService.js) relies on, and request validation. The query
 * itself is covered by CagQueueEntriesTest in the api module.
 */
public class CagQueueControllerTest {
	
	@Test
	public void entry_shouldUseTheFieldNamesTheQueueReads() {
		SimpleObject json = CagQueueController.toSimpleObject(new CagQueueEntry("visit", "member", "cag", "Demo CAG",
		        "cag-visit", "attender", "Test Moliehi"));
		
		assertEquals("visit", json.get("visitUuid"));
		assertEquals("member", json.get("patientUuid"));
		assertEquals("cag", json.get("cagUuid"));
		assertEquals("Demo CAG", json.get("cagName"));
		assertEquals("cag-visit", json.get("cagVisitUuid"));
		assertEquals("attender", json.get("presentMemberUuid"));
		assertEquals("Test Moliehi", json.get("presentMemberName"));
		assertEquals(false, json.get("isPresentMember"));
		assertEquals(
		    true,
		    CagQueueController.toSimpleObject(
		        new CagQueueEntry("visit", "attender", "cag", "Demo CAG", "cag-visit", "attender", "Test Moliehi")).get(
		        "isPresentMember"));
	}
	
	@Test(expected = IllegalRequestException.class)
	public void missingVisitUuids_shouldBeABadRequest() {
		withAuthenticatedUser().getQueueEntries(new HashMap<String, Object>());
	}
	
	@Test(expected = IllegalRequestException.class)
	public void visitUuidsNotAList_shouldBeABadRequest() {
		Map<String, Object> body = Collections.<String, Object> singletonMap("visitUuids", "abc");
		withAuthenticatedUser().getQueueEntries(body);
	}
	
	/** Skips the authentication check, which needs a running OpenMRS context. */
	private static CagQueueController withAuthenticatedUser() {
		return new CagQueueController() {
			
			@Override
			boolean isAuthenticated() {
				return true;
			}
		};
	}
}
