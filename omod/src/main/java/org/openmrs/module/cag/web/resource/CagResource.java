package org.openmrs.module.cag.web.resource;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.api.CagService;
import org.openmrs.module.cag.cag.Cag;
import org.openmrs.module.cag.cag.CagVisit;
import org.openmrs.module.cag.web.controller.CagController;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.api.Updatable;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Resource(name = RestConstants.VERSION_1 + CagController.CAG_NAMESPACE, supportedClass = Cag.class, supportedOpenmrsVersions = {
        "1.8.*", "1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.0.*", "2.1.*", "2.2.*", "2.3.*", "2.4.*", "2.5.*", "2.6.*",
        "2.7.*" })
@Component
public class CagResource extends DelegatingCrudResource<Cag> implements Updatable {
	
	/** Returns null for an unknown uuid, which the REST framework turns into a 404. */
	@Override
	public Cag getByUniqueId(String uuid) {
		Cag cag = getService().getCagByUuid(uuid);
		if (cag == null) {
			return null;
		}
		List<Patient> cagPatientList = getService().getCagAllPatients(cag.getId());
		cag.setCagPatientList(cagPatientList);

		return cag;
	}
	
	@Override
	protected void delete(Cag cag, String reason, RequestContext requestContext) throws ResponseException {
		getService().deleteCag(cag.getUuid());
	}
	
	@Override
	public Cag newDelegate() {
		return new Cag();
	}
	
	@Override
	public Cag save(Cag cag) {
		
		getService().saveCag(cag);
		
		return getService().getCagByUuid(cag.getUuid());
	}
	
	@Override
	public void purge(Cag cag, RequestContext requestContext) throws ResponseException {
		getService().deleteCag(cag.getUuid());
	}
	
	/**
	 * Updates only the fields present in the request; the others keep their stored value. Returns
	 * the REST representation, not the entity: returning the Hibernate entity made Jackson
	 * serialize its whole object graph (creator, patients, ...) and the request failed with a 500
	 * after the update had already been saved.
	 */
	@Override
	public Object update(String uuid, SimpleObject propertiesToUpdate, RequestContext context) throws ResponseException {
		Cag existing = getService().getCagByUuid(uuid);
		if (existing == null) {
			throw new ObjectNotFoundException();
		}

		Cag updatingCag = new Cag();
		updatingCag.setUuid(uuid);
		updatingCag.setName(value(propertiesToUpdate, "name", existing.getName()));
		updatingCag.setDescription(value(propertiesToUpdate, "description", existing.getDescription()));
		updatingCag.setConstituency(value(propertiesToUpdate, "constituency", existing.getConstituency()));
		updatingCag.setVillage(value(propertiesToUpdate, "village", existing.getVillage()));
		updatingCag.setDistrict(value(propertiesToUpdate, "district", existing.getDistrict()));

		Cag updated = getService().updateCag(updatingCag);
		return ConversionUtil.convertToRepresentation(updated, context.getRepresentation());
	}

	private static String value(SimpleObject properties, String name, String current) {
		if (!properties.containsKey(name)) {
			return current;
		}
		Object value = properties.get(name);
		return value == null ? null : value.toString();
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() throws ResourceDoesNotSupportOperationException {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("name");
		description.addProperty("description");
		description.addProperty("constituency");
		description.addProperty("village");
		description.addProperty("district");
		description.addProperty("date_changed");
		
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("name");
		description.addProperty("description");
		description.addProperty("constituency");
		description.addProperty("village");
		description.addProperty("district");
		description.addProperty("cagPatientList");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = null;
		
		if (representation instanceof DefaultRepresentation) {
			description = new DelegatingResourceDescription();
			
			description.addProperty("uuid");
			description.addProperty("name");
			description.addProperty("description");
			description.addProperty("village");
			description.addProperty("constituency");
			description.addProperty("district");
			description.addProperty("dateCreated");
			description.addProperty("cagPatientList", Representation.REF);
			
			description.addLink("full", ".?v=full");
		} else if (representation instanceof FullRepresentation) {
			description = new DelegatingResourceDescription();
			
			description.addProperty("uuid");
			description.addProperty("name");
			description.addProperty("description");
			description.addProperty("village");
			description.addProperty("constituency");
			description.addProperty("district");
			description.addProperty("dateCreated");
			description.addProperty("cagPatientList", Representation.REF);
			description.addProperty("creator", Representation.REF);
			
			description.addSelfLink();
		} else if (representation instanceof RefRepresentation) {
			description = new DelegatingResourceDescription();
			
			description.addProperty("uuid");
			description.addProperty("name");
			
			description.addLink("full", ".?v=full");
		}
		
		return description;
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		
		return new NeedsPaging<Cag>(getService().getAllCags(), context);
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) {
		String patientUuid = context.getParameter("patientuuid");

		Cag searchedCag = getService().searchCagByMemberUuid(patientUuid);
		// a patient in no CAG gives an empty result, not a list holding null (500 on conversion)
		List<Cag> results = searchedCag == null ? new ArrayList<Cag>() : new ArrayList<Cag>(
		        Collections.singletonList(searchedCag));
		return new NeedsPaging<Cag>(results, context);
	}
	
	private CagService getService() {
		return Context.getService(CagService.class);
	}
}
