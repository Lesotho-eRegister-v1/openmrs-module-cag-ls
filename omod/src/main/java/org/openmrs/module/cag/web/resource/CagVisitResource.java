package org.openmrs.module.cag.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.cag.api.CagService;
import org.openmrs.module.cag.cag.CagVisit;
import org.openmrs.module.cag.web.controller.CagController;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.api.Searchable;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.List;

@Resource(name = RestConstants.VERSION_1 + CagController.CAG_VISIT_NAMESPACE, supportedClass = CagVisit.class, supportedOpenmrsVersions = {
        "1.8.*", "1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.0.*", "2.1.*", "2.2.*", "2.3.*", "2.4.*", "2.5.*", "2.6.*",
        "2.7.*" })
@Component
public class CagVisitResource extends DelegatingCrudResource<CagVisit> implements Searchable {
	
	/**
	 * Closes the CAG visit. Returns the REST representation, not the entity: returning the
	 * Hibernate entity made Jackson serialize its whole object graph and the request failed with a
	 * 500 after the visit had already been closed.
	 */
	@Override
	public Object update(String uuid, SimpleObject propertiesToUpdate, RequestContext context) throws ResponseException {
		Object dateStopped = propertiesToUpdate.get("dateStopped");
		if (dateStopped == null) {
			throw new IllegalRequestException("dateStopped is required, as yyyy-MM-dd HH:mm:ss");
		}
		CagVisit closed;
		try {
			closed = getService().closeCagVisit(uuid, dateStopped.toString());
		}
		catch (ParseException e) {
			throw new IllegalRequestException("dateStopped must be yyyy-MM-dd HH:mm:ss, was: " + dateStopped);
		}
		if (closed == null) {
			throw new ObjectNotFoundException();
		}
		return ConversionUtil.convertToRepresentation(closed, context.getRepresentation());
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() throws ResourceDoesNotSupportOperationException {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("dateStopped");
		
		return description;
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		return new NeedsPaging<CagVisit>(getService().getCagAllVisits(), context);
	}
	
	/** Returns null for an unknown uuid, which the REST framework turns into a 404. */
	@Override
	public CagVisit getByUniqueId(String uuid) {
		return getService().getCagVisitByUuid(uuid);
	}

	@Override
	protected void delete(CagVisit cagVisit, String s, RequestContext requestContext) throws ResponseException {
		getService().deleteCagVisit(cagVisit.getUuid());
	}
	
	@Override
	public void purge(CagVisit cagVisit, RequestContext requestContext) throws ResponseException {
		
	}
	
	@Override
	public CagVisit newDelegate() {
		return new CagVisit();
	}
	
	@Override
	public CagVisit save(CagVisit cagVisit) {
		return getService().openCagVisit(cagVisit);
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) {
		
		String attenderUuid = context.getParameter("attenderuuid");
		Boolean isActive = Boolean.valueOf(context.getParameter("isactive"));

		List<CagVisit> searchedCagVisits = getService().searchCagVisits(attenderUuid, isActive);
		
		return new NeedsPaging<CagVisit>(searchedCagVisits, context);
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		
		description.addProperty("cag");
		description.addProperty("dateStarted");
		description.addProperty("attender");
		description.addProperty("absentees");
		description.addProperty("visits");
		description.addProperty("locationName");
		
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = null;
		
		if (representation instanceof DefaultRepresentation) {
			description = new DelegatingResourceDescription();
			
			description.addProperty("uuid");
			description.addProperty("dateStarted");
			description.addProperty("isActive");
			description.addProperty("dateStopped");
			description.addProperty("absentees");
			description.addProperty("cag", Representation.REF);
			description.addProperty("attender", Representation.REF);
			description.addProperty("visits", Representation.REF);
			
			description.addLink("full", ".?v=full");
		} else if (representation instanceof FullRepresentation) {
			description = new DelegatingResourceDescription();
			
			description.addProperty("uuid");
			description.addProperty("dateStarted");
			description.addProperty("isActive");
			description.addProperty("dateStopped");
			description.addProperty("absentees");
			description.addProperty("cag", Representation.REF);
			description.addProperty("attender", Representation.REF);
			description.addProperty("visits", Representation.FULL);
			
			description.addSelfLink();
		} else {
			description = new DelegatingResourceDescription();
			
			description.addProperty("uuid");
			description.addProperty("dateStarted");
			description.addProperty("isActive");
			description.addProperty("dateStopped");
			description.addProperty("absentees");
			description.addProperty("cag", Representation.REF);
			description.addProperty("attender", Representation.REF);
			description.addProperty("visits", Representation.FULL);
			
			description.addLink("full", ".?v=full");
		}
		
		return description;
	}
	
	private CagService getService() {
		return Context.getService(CagService.class);
	}
}
