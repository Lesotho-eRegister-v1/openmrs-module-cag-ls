package org.openmrs.module.cag.api.db.hibernate;

import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.cag.api.db.CagDao;
import org.openmrs.module.cag.cag.*;

import java.util.*;

public class HibernateCagDao implements CagDao {
	
	private DbSessionFactory sessionFactory;
	
	public DbSession getSession() {
		return sessionFactory.getCurrentSession();
	}
	
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	@Override
	public List<Cag> getAllCags() {
		Query query = getSession().createQuery("from cag c where c.voided= :voided");
		query.setInteger("voided", 0);
		return (List<Cag>) query.list();
	}
	
	@Override
	public void saveCag(Cag cag) {
		getSession().save(cag);
	}
	
	@Override
	public Cag getCagByUuid(String uuid) {
		Query query = getSession().createQuery("from cag c where c.uuid=:uuid and c.voided=:voided");
		query.setInteger("voided", 0);
		query.setString("uuid", uuid);
		return (Cag) query.uniqueResult();
	}
	
	@Override
	public Cag getCagByPatientUuid(String uuid) {
		Query query = getSession().createQuery("from cag_patient cp where cp.uuid=:uuid and cp.status=1");
		query.setString("uuid", uuid);
		CagPatient cagPatient = (CagPatient) query.uniqueResult();
		return (cagPatient != null) ? cagPatient.getCag() : null;
	}
	
	@Override
	public Cag updateCag(Cag cag) {
		Query query = getSession().createQuery(
		    "update cag c set c.name=:name, c.description=:description, c.village=:village,"
		            + " c.constituency=:constituency, c.district=:district, c.dateChanged=:date_changed,"
		            + " c.changedBy=:changed_by where c.uuid=:uuid and c.voided=false");
		query.setString("name", cag.getName());
		query.setString("description", cag.getDescription());
		query.setString("village", cag.getVillage());
		query.setString("constituency", cag.getConstituency());
		query.setString("district", cag.getDistrict());
		query.setDate("date_changed", cag.getDateChanged());
		query.setParameter("changed_by", cag.getChangedBy());
		query.setString("uuid", cag.getUuid());
		query.executeUpdate();
		return getCagByUuid(cag.getUuid());
	}
	
	@Override
	public void deleteCag(String cagUuid) {
		SQLQuery query = getSession()
		        .createSQLQuery(
		            "update cag c join cag_patient cp on c.cag_id=cp.cag_id set c.voided=1, cp.status=0 "
		                    + " where c.uuid=:cagUuid");
		query.setString("cagUuid", cagUuid);
		query.executeUpdate();
	}
	
	@Override
	public CagPatient getCagPatientById(Integer cagPatientId) {
		Query query = getSession().createQuery("from cag_patient cp where cp.cagPatientId=:id and cp.status=true");
		query.setInteger("id", cagPatientId);
		return (CagPatient) query.uniqueResult();
	}
	
	@Override
	public List<Patient> getAllCagPatients(Integer cagId) {
		Cag cag = new Cag(cagId);
		Query query = getSession().createQuery(
		    "select p from cag_patient cp join cp.patient p where cp.cag=:cag and cp.status=true");
		query.setParameter("cag", cag);
		return (List<Patient>) query.list();
	}
	
	@Override
	public void saveCagPatient(CagPatient cag_patient) {
		getSession().save(cag_patient);
	}
	
	@Override
	public void deletePatientFromCag(Integer patientId) {
		Patient patient = new Patient(patientId);
		Query query = getSession().createQuery(
		    "update cag_patient cp set cp.status=false where cp.patient=:patient and cp.status=true");
		query.setParameter("patient", patient);
		query.executeUpdate();
	}
	
	@Override
	public CagPatient getCagPatientByUuid(String cagPatientUuid) {
		Query query = getSession().createQuery("from cag_patient cp where cp.uuid=:uuid and cp.status=true");
		query.setString("uuid", cagPatientUuid);
		return (CagPatient) query.uniqueResult();
	}
	
	@Override
	public CagVisit saveCagVisit(CagVisit cagVisit) {
		getSession().save(cagVisit);
		return getCagVisitByUuid(cagVisit.getUuid());
	}
	
	@Override
	public CagVisit getCagVisitByUuid(String cagVisitUuid) {
		Query query = getSession().createQuery("from cag_visit cv where cv.uuid=:uuid and cv.voided=false");
		query.setString("uuid", cagVisitUuid);
		return (CagVisit) query.uniqueResult();
	}
	
	public List<CagVisit> getAllCagVisits() {
		Query query = getSession().createQuery("from cag_visit cv where cv.voided=false");
		return (List<CagVisit>) query.list();
	}
	
	@Override
	public List<CagVisit> searchCagVisits(Patient attender, Boolean isActive) {
		Query query = getSession().createQuery(
		    "from cag_visit cv where cv.attender=:attender and cv.isActive=:isActive and cv.voided=false");
		query.setParameter("attender", attender);
		query.setBoolean("isActive", isActive);
		return (List<CagVisit>) query.list();
	}
	
	@Override
	public void deleteCagVisit(String cagVisitUuid) {
		SQLQuery cagEncounterVoider = getSession().createSQLQuery(
		    "update obs o join encounter e on "
		            + "o.encounter_id=e.encounter_id join visit v on v.visit_id=e.visit_id join cag_visit_visit cvv on "
		            + "cvv.visit_id=v.visit_id join cag_visit cv on cv.cag_visit_id=cvv.cag_visit_id join cag_encounter ce "
		            + "on ce.cag_visit_id=cv.cag_visit_id set o.voided=1, e.voided=1, v.voided=1, cv.voided=1, ce.voided=1 "
		            + "where cv.uuid=:cagVisitUuid");
		cagEncounterVoider.setString("cagVisitUuid", cagVisitUuid);
		cagEncounterVoider.executeUpdate();
	}
	
	@Override
	public void saveAbsentCagPatient(Absentee absentee) {
		getSession().save(absentee);
	}
	
	@Override
	public List<Absentee> getAllAbsentees(Integer cagVisitId) {
		Query query = getSession().createQuery("from missed_drug_pickup  where cagVisitId=:cagVisitId");
		query.setInteger("cagVisitId", cagVisitId);
		return (List<Absentee>) query.list();
	}
	
	@Override
	public void closeCagPatientVisit(Patient patient, String startDate, String dateStopped) {
		Query query = getSession()
		        .createQuery(
		            "update Visit v set v.stopDatetime=:dateStopped where v.patient = :patient and v.startDatetime = :startDate and v.voided=false");
		query.setString("dateStopped", dateStopped);
		query.setString("startDate", startDate);
		query.setParameter("patient", patient);
		query.executeUpdate();
	}
	
	@Override
	public CagVisit closeCagVisit(String cagVisitUuid, String dateStopped) {
		Query query = getSession().createQuery(
		    "update cag_visit cv set cv.dateStopped=:dateStopped, isActive=false where cv.uuid=:uuid and cv.voided=false");
		query.setString("dateStopped", dateStopped);
		query.setString("uuid", cagVisitUuid);
		query.executeUpdate();
		return getCagVisitByUuid(cagVisitUuid);
	}
	
	@Override
	public List<CagEncounter> getAllCagEncounters() {
		Query query = getSession().createQuery("from cag_encounter ce where ce.voided=false");
		return (List<CagEncounter>) query.list();
	}
	
	@Override
	public List<CagEncounter> searchCagEncounters(Cag cag) {
		Query query = getSession().createQuery("from cag_encounter ce where ce.cag=:cag and ce.voided=0");
		query.setParameter("cag", cag);
		return (List<CagEncounter>) query.list();
	}
	
	@Override
	public CagEncounter getCagEncounterByUuid(String uuid) {
		Query query = getSession().createQuery("from cag_encounter ce where ce.uuid=:uuid and ce.voided=:voided");
		query.setInteger("voided", 0);
		query.setString("uuid", uuid);
		return (CagEncounter) query.uniqueResult();
	}
	
	@Override
	public void saveCagEncounter(CagEncounter cagEncounter) {
		getSession().save(cagEncounter);
	}
	
	@Override
	public void saveCagPatientEncounter(Encounter encounter) {
		getSession().save(encounter);
	}
	
	@Override
	public void updateCagEncounter(String cagEncounterUuid, Integer locationId, Date nextEncounterDateTime) {
		SQLQuery cagEncounterUpdater = getSession()
		        .createSQLQuery(
		            "update obs o join encounter e on "
		                    + "o.encounter_id=e.encounter_id join cag_encounter_encounter cee on "
		                    + "cee.encounter_id=e.encounter_id join cag_encounter ce on ce.cag_encounter_id=cee.cag_encounter_id "
		                    + "set o.location_id=:locationId, e.location_id=:locationId, ce.location_id=:locationId, ce.next_encounter_date=:nextEncounterDateTime "
		                    + "where ce.uuid=:cagEncounterUuid");
		cagEncounterUpdater.setString("cagEncounterUuid", cagEncounterUuid);
		cagEncounterUpdater.setParameter("nextEncounterDateTime", nextEncounterDateTime);
		cagEncounterUpdater.setParameter("locationId", locationId);
		cagEncounterUpdater.executeUpdate();
	}
	
	@Override
	public void deleteCagEncounter(String cagEncounterUuid) {
		SQLQuery cagEncounterVoider = getSession().createSQLQuery(
		    "update obs o join encounter e on " + "o.encounter_id=e.encounter_id join cag_encounter_encounter cee on "
		            + "cee.encounter_id=e.encounter_id join cag_encounter ce on ce.cag_encounter_id=cee.cag_encounter_id "
		            + "set o.voided=1, e.voided=1, ce.voided=1 where ce.uuid=:cagEncounterUuid");
		cagEncounterVoider.setString("cagEncounterUuid", cagEncounterUuid);
		cagEncounterVoider.executeUpdate();
	}
	
	@Override
	public Location getLocation(String locationUuid) {
		Query query = getSession().createQuery("from Location l where l.uuid=:locationUuid");
		query.setString("locationUuid", locationUuid);
		return (Location) query.uniqueResult();
	}
	
	@Override
	public CagVisit getActiveCagVisitContaining(Visit visit) {
		Query query = getSession().createQuery(
		    "select cv from cag_visit cv join cv.visits v where v = :visit and cv.voided = false and cv.isActive = true");
		query.setParameter("visit", visit);
		query.setMaxResults(1);
		return (CagVisit) query.uniqueResult();
	}
	
	@Override
	public CagEncounter getCagEncounterContaining(Encounter encounter) {
		Query query = getSession().createQuery(
		    "select ce from cag_encounter ce join ce.encounters e where e = :encounter and ce.voided = false");
		query.setParameter("encounter", encounter);
		query.setMaxResults(1);
		return (CagEncounter) query.uniqueResult();
	}
}
