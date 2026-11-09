package org.openmrs.module.cag.api.db;

import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.module.cag.cag.*;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository("cag.CagDao")
public interface CagDao {
	
	List<Cag> getAllCags();
	
	void saveCag(Cag cag);
	
	Cag getCagByUuid(String uuid);
	
	Cag getCagByPatientUuid(String uuid);
	
	Cag updateCag(Cag cag);
	
	CagPatient getCagPatientById(Integer cagPatientId);
	
	List<Patient> getAllCagPatients(Integer cagId);
	
	void saveCagPatient(CagPatient cagPatient);
	
	void deletePatientFromCag(Integer patientId);
	
	void deleteCag(String uuid);
	
	CagPatient getCagPatientByUuid(String uuid);
	
	CagVisit saveCagVisit(CagVisit cagVisit);
	
	CagVisit getCagVisitByUuid(String uuid);
	
	List<CagVisit> getAllCagVisits();
	
	List<CagVisit> searchCagVisits(Patient attender, Boolean isActive);
	
	void deleteCagVisit(String uuid);
	
	CagVisit closeCagVisit(String cagVisitUuid, String dateStopped);
	
	void saveAbsentCagPatient(Absentee absentee);
	
	List<Absentee> getAllAbsentees(Integer visitId);
	
	void closeCagPatientVisit(Patient patient, String startDate, String dateStopped);
	
	List<CagEncounter> getAllCagEncounters();
	
	CagEncounter getCagEncounterByUuid(String uuid);
	
	void saveCagEncounter(CagEncounter cagEncounter);
	
	void saveCagPatientEncounter(Encounter encounter);
	
	void updateCagEncounter(String cagEncounterUuid, Integer locationId, Date nextEncounterDateTime);
	
	void deleteCagEncounter(String uuid);
	
	List<CagEncounter> searchCagEncounters(Cag cag);
	
	Location getLocation(String uuid);
	
	/** The active, non-voided CAG visit that has {@code visit} among its member visits, or null. */
	CagVisit getActiveCagVisitContaining(Visit visit);
	
	/** The non-voided CAG encounter that links {@code encounter}, or null. */
	CagEncounter getCagEncounterContaining(Encounter encounter);
}
