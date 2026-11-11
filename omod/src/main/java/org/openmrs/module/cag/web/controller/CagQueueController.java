package org.openmrs.module.cag.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.api.CagQueueEntry;
import org.openmrs.module.cag.api.CagService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * CAG information for the Clinical patient queue, for all the queue's visits in one call:
 * <p>
 * {@code POST /openmrs/ws/rest/v1/cagqueue} with {@code "visitUuids": ["...", ...]}
 * <p>
 * returns {@code "results": [ visitUuid, patientUuid, cagUuid, cagName, cagVisitUuid,
 * presentMemberUuid, presentMemberName, isPresentMember}]}}, one entry per given visit that belongs
 * to an active CAG visit. POST rather than GET because a full queue's visit uuids can be longer
 * than a URL allows.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/cagqueue")
public class CagQueueController extends BaseRestController {
	
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public SimpleObject getQueueEntries(@RequestBody Map<String, Object> body) {
		if (!isAuthenticated()) {
			throw new APIAuthenticationException("Not authenticated");
		}
		Object visitUuids = body == null ? null : body.get("visitUuids");
		if (!(visitUuids instanceof List)) {
			throw new IllegalRequestException("visitUuids must be a list of visit uuids");
		}
		Set<String> uuids = new LinkedHashSet<String>();
		for (Object uuid : (List<?>) visitUuids) {
			if (uuid != null) {
				uuids.add(uuid.toString());
			}
		}
		
		List<SimpleObject> results = new ArrayList<SimpleObject>();
		for (CagQueueEntry entry : Context.getService(CagService.class).getQueueEntries(uuids)) {
			results.add(toSimpleObject(entry));
		}
		return new SimpleObject().add("results", results);
	}
	
	boolean isAuthenticated() {
		return Context.isAuthenticated();
	}
	
	static SimpleObject toSimpleObject(CagQueueEntry entry) {
		return new SimpleObject().add("visitUuid", entry.getVisitUuid()).add("patientUuid", entry.getPatientUuid())
		        .add("cagUuid", entry.getCagUuid()).add("cagName", entry.getCagName())
		        .add("cagVisitUuid", entry.getCagVisitUuid()).add("presentMemberUuid", entry.getPresentMemberUuid())
		        .add("presentMemberName", entry.getPresentMemberName()).add("isPresentMember", entry.isPresentMember());
	}
}
