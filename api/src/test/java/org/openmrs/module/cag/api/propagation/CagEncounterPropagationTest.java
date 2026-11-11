package org.openmrs.module.cag.api.propagation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.aopalliance.aop.Advice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.cag.api.CagService;
import org.openmrs.module.cag.cag.CagEncounter;
import org.openmrs.module.cag.cag.CagVisit;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reproduces the bug: a follow-up form saved for the CAG attender (the way Bahmni Clinical saves
 * it, through EncounterService.saveEncounter) must be mapped onto the other present members' CAG
 * visits. The hook is registered from the module's own config.xml, so a hook that exists but is not
 * declared there also fails these tests.
 */
public class CagEncounterPropagationTest extends BaseModuleContextSensitiveTest {
	
	private static final String CAG_UUID = "64feb3c1-601e-4ebb-9b55-265939125843";
	
	private static final String CAG_VISIT_UUID = "c616721e-ede2-4e79-8c7d-cac3745fb033";
	
	private static final String CONSULTATION = "81852aee-3f10-11e4-adec-0800271c1b75";
	
	private static final String REG = "81888515-3f10-11e4-adec-0800271c1b75";
	
	private static final String TEMPLATE = "746818ac-65a0-4d74-9609-ddb2c330a31b";
	
	private static final String REGIMEN_GROUP = "6fba4db0-4be0-48ab-b70f-68249959fbf2";
	
	private static final String DAYS_DISPENSED = "27d55083-5e66-4b5a-91d3-2c9a42cc9996";
	
	private static final String SUPPLY_DURATION = "9eb00622-1078-4f7b-aa69-61e6c36db347";
	
	private static final String ART_REGIMEN = "13382e01-9f18-488b-b2d2-58ab54c82d82";
	
	private static final String APPOINTMENT_SCHEDULED = "ed064424-0331-47f6-9532-77156f40a014";
	
	private static final String FOLLOW_UP_DATE = "88489023-783b-4021-b7a9-05ca9877bf67";
	
	private static final String TYPE_OF_CLIENT = "e0bc761d-ac3b-4033-92c7-476304b9c5e8";
	
	private static final String COTRIMOXAZOLE = "e8d05f4a-9c3f-4f99-941c-596f238f095f";
	
	private static final String FUNCTIONAL_STATUS = "0bb627e4-6ac6-4d9e-b540-7f2e21da0127";
	
	private static final String TREATMENT_BUDDY = "0f880c52-3ced-43ac-a79b-07a2740ae428";
	
	private static final String ART_PATIENT = "c0000000-0000-4000-8000-000000000031";
	
	private static final String TWO_MONTHS = "c0000000-0000-4000-8000-000000000032";
	
	private static final String YES = "c0000000-0000-4000-8000-000000000033";
	
	private static final String NOT_APPLICABLE = "c0000000-0000-4000-8000-000000000034";
	
	private static final String WORK = "c0000000-0000-4000-8000-000000000035";
	
	private static final String REGIMEN_1J = "c0000000-0000-4000-8000-000000000036";
	
	private static final String REGIMEN_1F = "c0000000-0000-4000-8000-000000000037";
	
	private final List<Advice> registeredAdvice = new ArrayList<Advice>();
	
	private Date followUpDate;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("CagPropagationTestDataset.xml");
		followUpDate = new SimpleDateFormat("yyyy-MM-dd").parse("2027-01-07");
		registerAdviceDeclaredInConfigXml();
	}
	
	@After
	public void tearDown() {
		for (Advice advice : registeredAdvice) {
			Context.removeAdvice(EncounterService.class, advice);
		}
	}
	
	@Test
	public void attenderFollowUp_shouldBeMappedOntoEachPresentMembersCagVisit() {
		Encounter source = saveAttenderFollowUp(58);
		
		Encounter member6 = onlyCopy(6, 102);
		Encounter member7 = onlyCopy(7, 103);
		
		for (Encounter copy : new Encounter[] { member6, member7 }) {
			assertEquals(CONSULTATION, copy.getEncounterType().getUuid());
			assertEquals(source.getEncounterDatetime(), copy.getEncounterDatetime());
			assertEquals(source.getLocation(), copy.getLocation());
			assertEquals("provider copied", 1, copy.getActiveEncounterProviders().size());
			
			Obs template = onlyTemplate(copy);
			assertEquals(copy.getPatient().getPerson(), template.getPerson());
			assertEquals(58.0, leaf(template, DAYS_DISPENSED).getValueNumeric(), 0.0);
			assertEquals(TWO_MONTHS, leaf(template, SUPPLY_DURATION).getValueCoded().getUuid());
			assertEquals(YES, leaf(template, APPOINTMENT_SCHEDULED).getValueCoded().getUuid());
			assertEquals(followUpDate, leaf(template, FOLLOW_UP_DATE).getValueDatetime());
			assertEquals(TREATMENT_BUDDY, leaf(template, TYPE_OF_CLIENT).getValueCoded().getUuid());
			// kept inside the same "Regimen" sub-group as on the attender's form
			assertEquals(REGIMEN_GROUP, leaf(template, DAYS_DISPENSED).getObsGroup().getConcept().getUuid());
			// attender-specific answers are not copied
			assertNull(leaf(template, COTRIMOXAZOLE));
			assertNull(leaf(template, FUNCTIONAL_STATUS));
		}
		// each member keeps their own regimen, never the attender's
		assertEquals(REGIMEN_1F, leaf(onlyTemplate(member6), ART_REGIMEN).getValueCoded().getUuid());
		assertNull("member without a regimen gets none", leaf(onlyTemplate(member7), ART_REGIMEN));
		
		// attender's own form untouched
		Obs attenderTemplate = onlyTemplate(source);
		assertEquals(ART_PATIENT, leaf(attenderTemplate, TYPE_OF_CLIENT).getValueCoded().getUuid());
		assertEquals(REGIMEN_1J, leaf(attenderTemplate, ART_REGIMEN).getValueCoded().getUuid());
		
		// group consultation recorded
		List<CagEncounter> cagEncounters = Context.getService(CagService.class).searchCagEncounters(CAG_UUID);
		assertEquals(1, cagEncounters.size());
		CagEncounter cagEncounter = cagEncounters.get(0);
		assertEquals(CAG_VISIT_UUID, cagEncounter.getCagVisit().getUuid());
		assertEquals(3, cagEncounter.getEncounters().size());
		assertTrue(cagEncounter.getEncounters().contains(source));
		assertTrue(cagEncounter.getEncounters().contains(member6));
		assertTrue(cagEncounter.getEncounters().contains(member7));
		assertEquals(followUpDate, cagEncounter.getNextEncounterDate());
	}
	
	@Test
	public void removedMember_shouldNotReceiveData() {
		saveAttenderFollowUp(58);
		assertEquals(0, encountersOnVisit(8, 104).size());
	}
	
	@Test
	public void editingTheAttenderForm_shouldUpdateCopiesWithoutDuplicates() {
		Encounter source = saveAttenderFollowUp(58);
		
		leaf(onlyTemplate(source), DAYS_DISPENSED).setValueNumeric(28.0);
		saveAndCommit(source);
		
		Encounter copy = onlyCopy(6, 102);
		assertEquals(28.0, leaf(onlyTemplate(copy), DAYS_DISPENSED).getValueNumeric(), 0.0);
		assertEquals(REGIMEN_1F, leaf(onlyTemplate(copy), ART_REGIMEN).getValueCoded().getUuid());
		assertEquals(1, Context.getService(CagService.class).searchCagEncounters(CAG_UUID).size());
		
		// saving again with nothing changed must not churn obs
		int before = allObsCount(copy);
		saveAndCommit(source);
		assertEquals(before, allObsCount(onlyCopy(6, 102)));
	}
	
	@Test
	public void memberConsultation_shouldNotPropagate() {
		Encounter memberOwn = followUp(patient(6), visit(102), CONSULTATION, 30);
		saveAndCommit(memberOwn);
		
		assertEquals(0, encountersOnVisit(2, 101).size());
		assertEquals(0, encountersOnVisit(7, 103).size());
	}
	
	@Test
	public void otherEncounterTypes_shouldNotPropagate() {
		saveAndCommit(followUp(patient(2), visit(101), REG, 58));
		
		assertEquals(0, encountersOnVisit(6, 102).size());
	}
	
	@Test
	public void closedCagVisit_shouldNotPropagate() {
		CagVisit cagVisit = Context.getService(CagService.class).getCagVisitByUuid(CAG_VISIT_UUID);
		cagVisit.setIsActive(false);
		Context.flushSession();
		
		saveAttenderFollowUp(58);
		assertEquals(0, encountersOnVisit(6, 102).size());
	}
	
	/**
	 * Live bug 2026-11-09 20:09: one Bahmni consultation save went through emrapi
	 * EmrEncounterServiceImpl.save, which switches the session to FlushMode.MANUAL and calls
	 * saveEncounter several times. Each call created another set of copies (3 per member), which
	 * then broke Clinical for those members ("More than one encounter matches the criteria").
	 */
	@Test
	public void severalSavesInOneManualFlushSave_likeEmrapi_shouldMapOnce() {
		Session session = Context.getRegisteredComponent("sessionFactory", SessionFactory.class).getCurrentSession();
		FlushMode previous = session.getHibernateFlushMode();
		Encounter source = followUp(patient(2), visit(101), CONSULTATION, 58);
		session.setHibernateFlushMode(FlushMode.MANUAL);
		try {
			Context.getEncounterService().saveEncounter(source);
			Context.getEncounterService().saveEncounter(source);
			Context.getEncounterService().saveEncounter(source);
		}
		finally {
			session.setHibernateFlushMode(previous);
		}
		commit();
		
		onlyCopy(6, 102);
		onlyCopy(7, 103);
		assertEquals(1, Context.getService(CagService.class).searchCagEncounters(CAG_UUID).size());
	}
	
	// ---------------------------------------------------------------------------------------------
	
	private void saveAndCommit(Encounter encounter) {
		Context.getEncounterService().saveEncounter(encounter);
		commit();
	}
	
	/** What Spring does at commit, short of committing (the test transaction is rolled back). */
	private void commit() {
		TransactionSynchronizationUtils.triggerBeforeCommit(false);
		Context.flushSession();
	}
	
	private Encounter saveAttenderFollowUp(double daysDispensed) {
		Encounter source = followUp(patient(2), visit(101), CONSULTATION, daysDispensed);
		saveAndCommit(source);
		return source;
	}
	
	/**
	 * Same shape as a real v1 form: Template -> [Regimen -> (...), Type of client, Functional
	 * Status].
	 */
	private Encounter followUp(Patient patient, Visit visit, String encounterTypeUuid, double daysDispensed) {
		Encounter e = new Encounter();
		e.setPatient(patient);
		e.setVisit(visit);
		e.setEncounterType(Context.getEncounterService().getEncounterTypeByUuid(encounterTypeUuid));
		e.setEncounterDatetime(new Date(visit.getStartDatetime().getTime() + 2 * 60 * 60 * 1000L));
		e.setLocation(visit.getLocation());
		Provider provider = Context.getProviderService().getProvider(1);
		EncounterRole role = Context.getEncounterService().getEncounterRole(1);
		e.addProvider(role, provider);
		
		Obs regimenGroup = obs(REGIMEN_GROUP);
		regimenGroup.addGroupMember(numeric(DAYS_DISPENSED, daysDispensed));
		regimenGroup.addGroupMember(coded(SUPPLY_DURATION, TWO_MONTHS));
		regimenGroup.addGroupMember(coded(ART_REGIMEN, REGIMEN_1J));
		regimenGroup.addGroupMember(coded(COTRIMOXAZOLE, NOT_APPLICABLE));
		regimenGroup.addGroupMember(coded(APPOINTMENT_SCHEDULED, YES));
		Obs followUp = obs(FOLLOW_UP_DATE);
		followUp.setValueDatetime(followUpDate);
		regimenGroup.addGroupMember(followUp);
		
		Obs template = obs(TEMPLATE);
		template.addGroupMember(regimenGroup);
		template.addGroupMember(coded(TYPE_OF_CLIENT, ART_PATIENT));
		template.addGroupMember(coded(FUNCTIONAL_STATUS, WORK));
		e.addObs(template);
		return e;
	}
	
	private Obs obs(String conceptUuid) {
		Obs o = new Obs();
		o.setConcept(concept(conceptUuid));
		return o;
	}
	
	private Obs numeric(String conceptUuid, double value) {
		Obs o = obs(conceptUuid);
		o.setValueNumeric(value);
		return o;
	}
	
	private Obs coded(String conceptUuid, String answerUuid) {
		Obs o = obs(conceptUuid);
		o.setValueCoded(concept(answerUuid));
		return o;
	}
	
	private Concept concept(String uuid) {
		Concept c = Context.getConceptService().getConceptByUuid(uuid);
		assertNotNull(uuid, c);
		return c;
	}
	
	private Patient patient(int id) {
		return Context.getPatientService().getPatient(id);
	}
	
	private Visit visit(int id) {
		return Context.getVisitService().getVisit(id);
	}
	
	private List<Encounter> encountersOnVisit(int patientId, int visitId) {
		List<Encounter> result = new ArrayList<Encounter>();
		for (Encounter e : Context.getEncounterService().getEncountersByPatient(patient(patientId))) {
			if (!e.getVoided() && e.getVisit() != null && e.getVisit().getVisitId() == visitId) {
				result.add(e);
			}
		}
		return result;
	}
	
	private Encounter onlyCopy(int patientId, int visitId) {
		List<Encounter> copies = encountersOnVisit(patientId, visitId);
		assertEquals("encounters for patient " + patientId + " on CAG visit", 1, copies.size());
		return copies.get(0);
	}
	
	private Obs onlyTemplate(Encounter e) {
		List<Obs> found = new ArrayList<Obs>();
		for (Obs o : e.getObsAtTopLevel(false)) {
			if (TEMPLATE.equals(o.getConcept().getUuid())) {
				found.add(o);
			}
		}
		assertEquals("non-voided follow-up forms", 1, found.size());
		return found.get(0);
	}
	
	private Obs leaf(Obs group, String conceptUuid) {
		for (Obs member : group.getGroupMembers(false)) {
			if (conceptUuid.equals(member.getConcept().getUuid())) {
				return member;
			}
			if (member.isObsGrouping()) {
				Obs nested = leaf(member, conceptUuid);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}
	
	private int allObsCount(Encounter e) {
		return e.getAllObs(true).size();
	}
	
	/**
	 * Registers exactly what the deployed module would: the EncounterService <advice> entries of
	 * omod/src/main/resources/config.xml (read from source; surefire runs with basedir = api/).
	 */
	private void registerAdviceDeclaredInConfigXml() throws Exception {
		File config = new File(System.getProperty("basedir", "."), "../omod/src/main/resources/config.xml");
		assertTrue("module config.xml at " + config.getCanonicalPath(), config.isFile());
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config);
		
		NodeList advices = doc.getDocumentElement().getElementsByTagName("advice");
		for (int i = 0; i < advices.getLength(); i++) {
			Element advice = (Element) advices.item(i);
			String point = advice.getElementsByTagName("point").item(0).getTextContent().trim();
			String className = advice.getElementsByTagName("class").item(0).getTextContent().trim();
			if (EncounterService.class.getName().equals(point)) {
				Advice instance = (Advice) Class.forName(className).newInstance();
				Context.addAdvice(EncounterService.class, instance);
				registeredAdvice.add(instance);
			}
		}
	}
}
