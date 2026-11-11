package org.openmrs.module.cag.api.propagation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.CagConstants;
import org.openmrs.module.cag.api.db.CagDao;
import org.openmrs.module.cag.cag.Cag;
import org.openmrs.module.cag.cag.CagEncounter;
import org.openmrs.module.cag.cag.CagPatient;
import org.openmrs.module.cag.cag.CagVisit;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Maps the follow-up form saved for a CAG attender onto the CAG visits of the other present
 * members, the way the Bahmni 0.92 Clinical app used to do in the browser:
 * <ul>
 * <li>only when the encounter is on the attender's visit of an active CAG visit;</li>
 * <li>each member gets one encounter on their own CAG visit, with the same type, time, location and
 * providers;</li>
 * <li>its form keeps only the configured shared answers (supply, days dispensed, appointment,
 * follow-up date) in the same groups as the attender's form, sets "Type of client" to the buddy
 * answer, and fills the regimen with the member's own latest regimen (left out if they have none);</li>
 * <li>re-saving the attender's form updates the members' copies instead of adding new ones.</li>
 * </ul>
 * The source, copies and CAG visit are linked by one {@link CagEncounter}. Runs in the caller's
 * transaction, so a failure rolls the attender's save back instead of leaving members half-updated.
 */
@Component("cag.encounterPropagator")
public class CagEncounterPropagator {
	
	private static final Log log = LogFactory.getLog(CagEncounterPropagator.class);
	
	static final String VOID_REASON = "Replaced by updated CAG attender follow-up";
	
	private static final String[] PRIVILEGES = { PrivilegeConstants.GET_GLOBAL_PROPERTIES, PrivilegeConstants.GET_VISITS,
	        PrivilegeConstants.GET_ENCOUNTERS, PrivilegeConstants.ADD_ENCOUNTERS, PrivilegeConstants.EDIT_ENCOUNTERS,
	        PrivilegeConstants.GET_OBS, PrivilegeConstants.ADD_OBS, PrivilegeConstants.EDIT_OBS,
	        PrivilegeConstants.DELETE_OBS, PrivilegeConstants.GET_PATIENTS, PrivilegeConstants.GET_CONCEPTS,
	        PrivilegeConstants.GET_PROVIDERS, PrivilegeConstants.GET_ENCOUNTER_ROLES, PrivilegeConstants.GET_LOCATIONS };
	
	@Autowired
	@Qualifier("cagDao")
	private CagDao dao;
	
	public void setDao(CagDao dao) {
		this.dao = dao;
	}
	
	/**
	 * @return the members' encounters that now carry the mapped form (empty if nothing applied)
	 */
	public List<Encounter> propagate(Encounter source) {
		if (source == null || source.getVoided() || source.getVisit() == null || source.getEncounterType() == null
		        || source.getPatient() == null) {
			return Collections.emptyList();
		}
		for (String privilege : PRIVILEGES) {
			Context.addProxyPrivilege(privilege);
		}
		try {
			Settings settings = Settings.load();
			if (!settings.enabled || !settings.encounterTypeUuids.contains(source.getEncounterType().getUuid())) {
				return Collections.emptyList();
			}
			return propagate(source, settings);
		}
		finally {
			for (String privilege : PRIVILEGES) {
				Context.removeProxyPrivilege(privilege);
			}
		}
	}
	
	private List<Encounter> propagate(Encounter source, Settings settings) {
		CagVisit cagVisit = dao.getActiveCagVisitContaining(source.getVisit());
		if (cagVisit == null) {
			return Collections.emptyList();
		}
		Patient attender = cagVisit.getAttender();
		if (attender == null || !attender.equals(source.getPatient())) {
			log.debug("Encounter " + source.getUuid() + " is on CAG visit " + cagVisit.getUuid()
			        + " but not for its attender; not propagated");
			return Collections.emptyList();
		}
		
		List<Obs> template = formRoots(source, settings);
		CagEncounter cagEncounter = dao.getCagEncounterContaining(source);
		
		if (template.isEmpty()) {
			log.info("Attender encounter " + source.getUuid() + " on CAG visit " + cagVisit.getUuid()
			        + " has no CAG follow-up form (concept " + settings.formConceptUuid + " or forms " + settings.formNames
			        + "); nothing propagated");
			// form removed from the attender's encounter: remove it from the copies too
			if (cagEncounter != null) {
				for (Encounter copy : copiesIn(cagEncounter, source)) {
					if (voidForms(copy, settings)) {
						Context.getEncounterService().saveEncounter(copy);
					}
				}
			}
			return Collections.emptyList();
		}
		
		if (cagEncounter == null) {
			cagEncounter = new CagEncounter();
			cagEncounter.setCag(cagVisit.getCag());
			cagEncounter.setCagVisit(cagVisit);
			cagEncounter.setAttender(attender);
			cagEncounter.setCagEncounterDateTime(source.getEncounterDatetime());
			cagEncounter.setCreator(Context.getAuthenticatedUser());
			cagEncounter.setDateCreated(new Date());
			cagEncounter.getEncounters().add(source);
		}
		cagEncounter.setLocation(source.getLocation());
		Obs followUp = findLeaf(template, settings.followUpDateConceptUuid);
		cagEncounter.setNextEncounterDate(followUp == null ? null : followUp.getValueDatetime());
		
		Concept buddyAnswer = concept(settings.buddyAnswerUuid);
		List<Encounter> result = new ArrayList<Encounter>();
		
		for (Visit memberVisit : cagVisit.getVisits()) {
			Patient member = memberVisit.getPatient();
			if (memberVisit.getVoided() || member == null || member.equals(attender)) {
				continue;
			}
			if (!isActiveMember(member, cagVisit.getCag())) {
				log.info("Patient " + member.getUuid() + " is no longer an active member of CAG "
				        + cagVisit.getCag().getUuid() + "; CAG follow-up not copied");
				continue;
			}
			Date when = source.getEncounterDatetime();
			if (when.before(memberVisit.getStartDatetime())) {
				when = memberVisit.getStartDatetime();
			}
			if (memberVisit.getStopDatetime() != null && when.after(memberVisit.getStopDatetime())) {
				log.warn("CAG visit " + memberVisit.getUuid() + " of patient " + member.getUuid()
				        + " ended before the attender's encounter " + source.getUuid() + "; CAG follow-up not copied");
				continue;
			}
			
			List<Obs> memberForm = mapForm(template, member, settings, buddyAnswer);
			if (memberForm.isEmpty()) {
				log.warn("CAG follow-up of encounter " + source.getUuid() + " has nothing to copy for patient "
				        + member.getUuid());
				continue;
			}
			Encounter copy = copyFor(cagEncounter, member, source);
			if (copy == null) {
				copy = new Encounter();
				copy.setPatient(member);
				copy.setVisit(memberVisit);
				copy.setEncounterType(source.getEncounterType());
				copy.setEncounterDatetime(when);
				copy.setLocation(source.getLocation());
				copy.setForm(source.getForm());
				for (EncounterProvider provider : source.getActiveEncounterProviders()) {
					copy.addProvider(provider.getEncounterRole(), provider.getProvider());
				}
			} else {
				if (signature(formRoots(copy, settings)).equals(signature(memberForm))) {
					result.add(copy);
					continue;
				}
				voidForms(copy, settings);
				copy.setLocation(source.getLocation());
			}
			for (Obs obs : memberForm) {
				copy.addObs(obs);
			}
			Context.getEncounterService().saveEncounter(copy);
			cagEncounter.getEncounters().add(copy);
			result.add(copy);
		}
		
		dao.saveCagEncounter(cagEncounter);
		log.info("CAG follow-up of attender encounter " + source.getUuid() + " mapped to " + result.size()
		        + " member(s) of CAG visit " + cagVisit.getUuid());
		return result;
	}
	
	/**
	 * Copies the form's top-level obs keeping only shared answers, inside the same groups, with the
	 * member's own regimen and the buddy client type. Works for the legacy observation template
	 * (one wrapper obs) and for Form Builder forms (several top-level obs). Groups left with no
	 * answers are dropped; returns an empty list if nothing is left.
	 */
	private List<Obs> mapForm(List<Obs> template, Patient member, Settings settings, Concept buddyAnswer) {
		boolean[] clientTypePlaced = { false };
		List<Obs> form = new ArrayList<Obs>();
		Obs legacyWrapper = null;
		for (Obs root : template) {
			Obs mapped = mapObs(root, member, settings, buddyAnswer, clientTypePlaced);
			if (mapped == null && settings.formConceptUuid.equals(root.getConcept().getUuid())) {
				mapped = newObsLike(root);
			}
			if (mapped != null) {
				form.add(mapped);
				if (settings.formConceptUuid.equals(mapped.getConcept().getUuid())) {
					legacyWrapper = mapped;
				}
			}
		}
		if (!clientTypePlaced[0] && buddyAnswer != null) {
			Concept clientType = concept(settings.clientTypeConceptUuid);
			if (clientType != null) {
				Obs type = new Obs();
				type.setConcept(clientType);
				type.setValueCoded(buddyAnswer);
				if (legacyWrapper != null) {
					legacyWrapper.addGroupMember(type);
				} else {
					form.add(type);
				}
			}
		}
		List<Obs> result = new ArrayList<Obs>();
		for (Obs obs : form) {
			if (obs != legacyWrapper || obs.hasGroupMembers(false)) {
				result.add(obs);
			}
		}
		return result;
	}
	
	private Obs mapObs(Obs source, Patient member, Settings settings, Concept buddyAnswer, boolean[] clientTypePlaced) {
		if (source.hasGroupMembers(false)) {
			Obs group = newObsLike(source);
			for (Obs child : members(source)) {
				Obs mapped = mapObs(child, member, settings, buddyAnswer, clientTypePlaced);
				if (mapped != null) {
					group.addGroupMember(mapped);
				}
			}
			return group.hasGroupMembers(false) ? group : null;
		}
		
		String conceptUuid = source.getConcept().getUuid();
		if (conceptUuid.equals(settings.clientTypeConceptUuid)) {
			if (buddyAnswer == null) {
				return null;
			}
			Obs type = newObsLike(source);
			type.setValueCoded(buddyAnswer);
			clientTypePlaced[0] = true;
			return type;
		}
		if (conceptUuid.equals(settings.ownRegimenConceptUuid)) {
			Obs ownRegimen = latestObs(member, source.getConcept());
			if (ownRegimen == null) {
				return null;
			}
			Obs regimen = newObsLike(source);
			copyValue(ownRegimen, regimen);
			return regimen;
		}
		if (settings.copiedConceptUuids.contains(conceptUuid)) {
			Obs copied = newObsLike(source);
			copyValue(source, copied);
			return copied;
		}
		return null;
	}
	
	private static Obs newObsLike(Obs source) {
		Obs obs = new Obs();
		obs.setConcept(source.getConcept());
		if (source.getFormFieldNamespace() != null || source.getFormFieldPath() != null) {
			obs.setFormField(source.getFormFieldNamespace(), source.getFormFieldPath());
		}
		return obs;
	}
	
	private static void copyValue(Obs from, Obs to) {
		to.setValueCoded(from.getValueCoded());
		to.setValueCodedName(from.getValueCodedName());
		to.setValueDrug(from.getValueDrug());
		to.setValueNumeric(from.getValueNumeric());
		to.setValueDatetime(from.getValueDatetime());
		to.setValueText(from.getValueText());
		to.setValueModifier(from.getValueModifier());
	}
	
	private Obs latestObs(Person person, Concept question) {
		List<Obs> latest = Context.getObsService().getObservations(Collections.singletonList(person), null,
		    Collections.singletonList(question), null, null, null, Collections.singletonList("obsDatetime"), 1, null, null,
		    null, false);
		return latest.isEmpty() ? null : latest.get(0);
	}
	
	private boolean isActiveMember(Patient patient, Cag cag) {
		CagPatient membership = dao.getCagPatientByUuid(patient.getUuid());
		return membership != null && membership.getCag() != null && membership.getCag().getId().equals(cag.getId());
	}
	
	private static Encounter copyFor(CagEncounter cagEncounter, Patient member, Encounter source) {
		for (Encounter e : cagEncounter.getEncounters()) {
			if (!e.equals(source) && !e.getVoided() && member.equals(e.getPatient())) {
				return e;
			}
		}
		return null;
	}
	
	private static List<Encounter> copiesIn(CagEncounter cagEncounter, Encounter source) {
		List<Encounter> copies = new ArrayList<Encounter>();
		for (Encounter e : cagEncounter.getEncounters()) {
			if (!e.equals(source) && !e.getVoided()) {
				copies.add(e);
			}
		}
		return copies;
	}
	
	private static boolean voidForms(Encounter encounter, Settings settings) {
		List<Obs> forms = formRoots(encounter, settings);
		for (Obs form : forms) {
			Context.getObsService().voidObs(form, VOID_REASON);
		}
		return !forms.isEmpty();
	}
	
	/**
	 * Top-level obs of the CAG follow-up form: the legacy template obs, or any obs a Form Builder
	 * form in {@code cag.propagation.formNames} saved (form path "Bahmni^<name>.<version>/...").
	 */
	static List<Obs> formRoots(Encounter encounter, Settings settings) {
		List<Obs> found = new ArrayList<Obs>();
		for (Obs obs : encounter.getObsAtTopLevel(false)) {
			if (settings.formConceptUuid.equals(obs.getConcept().getUuid()) || isOnForm(obs, settings.formNames)) {
				found.add(obs);
			}
		}
		return found;
	}
	
	private static boolean isOnForm(Obs obs, Set<String> formNames) {
		String path = obs.getFormFieldPath();
		if (!"Bahmni".equals(obs.getFormFieldNamespace()) || path == null) {
			return false;
		}
		for (String name : formNames) {
			if (path.startsWith(name + ".")) {
				return true;
			}
		}
		return false;
	}
	
	private static Obs findLeaf(List<Obs> roots, String conceptUuid) {
		for (Obs root : roots) {
			if (conceptUuid.equals(root.getConcept().getUuid())) {
				return root;
			}
			Obs nested = findLeaf(root, conceptUuid);
			if (nested != null) {
				return nested;
			}
		}
		return null;
	}
	
	private static Obs findLeaf(Obs group, String conceptUuid) {
		for (Obs member : members(group)) {
			if (conceptUuid.equals(member.getConcept().getUuid())) {
				return member;
			}
			Obs nested = findLeaf(member, conceptUuid);
			if (nested != null) {
				return nested;
			}
		}
		return null;
	}
	
	/** Order-independent description of a form's answers, to skip rewriting unchanged copies. */
	static String signature(List<Obs> roots) {
		List<String> parts = new ArrayList<String>();
		for (Obs obs : roots) {
			parts.add(signature(obs));
		}
		Collections.sort(parts);
		return parts.toString();
	}
	
	static String signature(Obs obs) {
		StringBuilder sb = new StringBuilder(obs.getConcept().getUuid()).append('=');
		if (obs.getValueCoded() != null) {
			sb.append(obs.getValueCoded().getUuid());
		}
		if (obs.getValueDrug() != null) {
			sb.append(obs.getValueDrug().getUuid());
		}
		if (obs.getValueNumeric() != null) {
			sb.append(obs.getValueNumeric());
		}
		if (obs.getValueDatetime() != null) {
			sb.append(obs.getValueDatetime().getTime());
		}
		if (obs.getValueText() != null) {
			sb.append(obs.getValueText());
		}
		List<String> children = new ArrayList<String>();
		for (Obs child : members(obs)) {
			children.add(signature(child));
		}
		Collections.sort(children);
		return sb.append(children).toString();
	}
	
	/** Non-voided group members; Obs.getGroupMembers(false) returns null for a leaf. */
	private static Set<Obs> members(Obs obs) {
		Set<Obs> members = obs.getGroupMembers(false);
		return members == null ? Collections.<Obs> emptySet() : members;
	}
	
	private static Concept concept(String uuid) {
		Concept concept = Context.getConceptService().getConceptByUuid(uuid);
		if (concept == null) {
			log.warn("CAG propagation: concept " + uuid + " not found; check the cag.propagation.* global properties");
		}
		return concept;
	}
	
	static class Settings {
		
		boolean enabled;
		
		Set<String> encounterTypeUuids;
		
		String formConceptUuid;
		
		Set<String> formNames;
		
		Set<String> copiedConceptUuids;
		
		String ownRegimenConceptUuid;
		
		String clientTypeConceptUuid;
		
		String buddyAnswerUuid;
		
		String followUpDateConceptUuid;
		
		static Settings load() {
			Settings s = new Settings();
			s.enabled = !"false".equalsIgnoreCase(gp(CagConstants.GP_PROPAGATION_ENABLED, "true"));
			s.encounterTypeUuids = list(gp(CagConstants.GP_ENCOUNTER_TYPE_UUIDS, CagConstants.DEFAULT_ENCOUNTER_TYPE_UUIDS));
			s.formConceptUuid = gp(CagConstants.GP_FORM_CONCEPT_UUID, CagConstants.DEFAULT_FORM_CONCEPT_UUID);
			s.formNames = list(gp(CagConstants.GP_FORM_NAMES, CagConstants.DEFAULT_FORM_NAMES));
			s.copiedConceptUuids = list(gp(CagConstants.GP_COPIED_CONCEPT_UUIDS, CagConstants.DEFAULT_COPIED_CONCEPT_UUIDS));
			s.ownRegimenConceptUuid = gp(CagConstants.GP_OWN_REGIMEN_CONCEPT_UUID,
			    CagConstants.DEFAULT_OWN_REGIMEN_CONCEPT_UUID);
			s.clientTypeConceptUuid = gp(CagConstants.GP_CLIENT_TYPE_CONCEPT_UUID,
			    CagConstants.DEFAULT_CLIENT_TYPE_CONCEPT_UUID);
			s.buddyAnswerUuid = gp(CagConstants.GP_CLIENT_TYPE_BUDDY_ANSWER_UUID,
			    CagConstants.DEFAULT_CLIENT_TYPE_BUDDY_ANSWER_UUID);
			s.followUpDateConceptUuid = gp(CagConstants.GP_FOLLOW_UP_DATE_CONCEPT_UUID,
			    CagConstants.DEFAULT_FOLLOW_UP_DATE_CONCEPT_UUID);
			return s;
		}
		
		private static String gp(String name, String defaultValue) {
			String value = Context.getAdministrationService().getGlobalProperty(name);
			return StringUtils.isBlank(value) ? defaultValue : value.trim();
		}
		
		private static Set<String> list(String value) {
			Set<String> set = new HashSet<String>();
			for (String item : Arrays.asList(value.split(","))) {
				if (StringUtils.isNotBlank(item)) {
					set.add(item.trim());
				}
			}
			return set;
		}
	}
}
