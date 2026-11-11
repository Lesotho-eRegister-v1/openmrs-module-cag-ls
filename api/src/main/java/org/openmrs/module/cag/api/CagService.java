package org.openmrs.module.cag.api;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.openmrs.Patient;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.cag.cag.Cag;
import org.openmrs.module.cag.cag.CagEncounter;
import org.openmrs.module.cag.cag.CagPatient;
import org.openmrs.module.cag.cag.CagVisit;

public interface CagService extends OpenmrsService {
	
	Cag getCagByUuid(String uuid);
	
	List<Cag> getAllCags();
	
	void saveCag(Cag cag);
	
	CagPatient getCagPatientById(Integer cagPatientId);
	
	Cag searchCagByMemberUuid(String uuid);
	
	List<Patient> getCagAllPatients(Integer id);
	
	Patient saveCagPatient(CagPatient cagPatient);
	
	void deletePatientFromCag(String uuid);
	
	void deleteCag(String uuid);
	
	Cag updateCag(Cag cag);
	
	CagPatient getCagPatientByUuid(String uuid);
	
	CagVisit openCagVisit(CagVisit cagVisit);
	
	CagVisit getCagVisitByUuid(String uuid);
	
	List<CagVisit> getCagAllVisits();
	
	List<CagVisit> searchCagVisits(String attenderUuid, Boolean isActive);
	
	void deleteCagVisit(String uuid);
	
	CagVisit closeCagVisit(String uuid, String dateStopped) throws ParseException;
	
	List<CagEncounter> getAllCagEncounters();
	
	List<CagEncounter> searchCagEncounters(String uuid);
	
	CagEncounter getCagEncounterByUuid(String uuid);
	
	CagEncounter saveCagEncounter(CagEncounter cagEncounter);
	
	CagEncounter updateCagEncounter(String uuid, String locationUuid, Date nextEncounterDateTime);
	
	void deleteCagEncounter(String uuid);
	
	/**
	 * For the Clinical patient queue: one entry per given visit that belongs to an active CAG
	 * visit. Visits that are not part of an active CAG visit are left out.
	 */
	List<CagQueueEntry> getQueueEntries(Collection<String> visitUuids);
	
}
