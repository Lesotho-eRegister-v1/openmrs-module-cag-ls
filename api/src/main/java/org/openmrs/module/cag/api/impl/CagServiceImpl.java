/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.cag.api.impl;

import org.openmrs.*;
import org.openmrs.api.OrderContext;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.cag.api.CagService;
import org.openmrs.module.cag.api.db.CagDao;
import org.openmrs.module.cag.cag.*;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Transactional
public class CagServiceImpl extends BaseOpenmrsService implements CagService {
	
	private CagDao dao;
	
	public CagDao getDao() {
		return dao;
	}
	
	public void setDao(CagDao dao) {
		this.dao = dao;
	}
	
	@Override
	public Cag getCagByUuid(String uuid) {
		Cag cag = dao.getCagByUuid(uuid);
		List<Patient> cagPatientList = getCagAllPatients(cag.getId());
		cag.setCagPatientList(cagPatientList);
		return cag;
	}
	
	@Override
	public List<Cag> getAllCags() {
		return dao.getAllCags();
	}
	
	@Override
	public void saveCag(Cag cag) {
		cag.setCreator(Context.getAuthenticatedUser());
		cag.setDateCreated(new Date());
		cag.setVoided(false);
		if (cag.getUuid() == null) {
			cag.setUuid(UUID.randomUUID().toString());
		}
		dao.saveCag(cag);
		List<Patient> patients = cag.getCagPatientList();
		
		if (patients == null) {
			return;
		}
		for (Patient currentPatient : patients) {
			CagPatient newCagPatient = new CagPatient();
			newCagPatient.setCag(cag);
			newCagPatient.setPatient(currentPatient);
			saveCagPatient(newCagPatient);
		}
	}
	
	@Override
	public void onStartup() {
	}
	
	@Override
	public void onShutdown() {
	}
	
	@Override
	public CagPatient getCagPatientById(Integer cagPatientId) {
		return this.dao.getCagPatientById(cagPatientId);
	}
	
	@Override
	public Cag searchCagByMemberUuid(String uuid) {
		
		Cag searchedCag = this.dao.getCagByPatientUuid(uuid);
		
		if (searchedCag != null) {
			List<Patient> patients = this.dao.getAllCagPatients(searchedCag.getId());
			searchedCag.setCagPatientList(patients);
		}
		
		return searchedCag;
	}
	
	@Override
	public List<Patient> getCagAllPatients(Integer cagId) {
		return dao.getAllCagPatients(cagId);
	}
	
	@Override
	public Patient saveCagPatient(CagPatient cagPatient) {
		CagPatient retrivedCagPatient = getCagPatientByUuid(cagPatient.getPatient().getUuid());
		
		if ((retrivedCagPatient != null)) {
			throw new IllegalArgumentException("WARNING!! Patient : " + retrivedCagPatient.getPatient().getNames()
			        + "(uuid=" + retrivedCagPatient.getPatient().getUuid() + ") " + " Already a member of another CAG:"
			        + " " + retrivedCagPatient.getCag().getName());
			
		} else {
			cagPatient.setUuid(cagPatient.getPatient().getUuid());
			cagPatient.setStatus(true);
			dao.saveCagPatient(cagPatient);
			return cagPatient.getPatient();
		}
	}
	
	@Override
	public void deletePatientFromCag(String uuid) {
		Integer patientId = Context.getPatientService().getPatientByUuid(uuid).getPatientId();
		dao.deletePatientFromCag(patientId);
	}
	
	@Override
	public void deleteCag(String uuid) {
		dao.deleteCag(uuid);
	}
	
	@Override
	public Cag updateCag(Cag cag) {
		cag.setDateChanged(new Date());
		cag.setChangedBy(Context.getAuthenticatedUser());
		
		Cag updatedCag = dao.updateCag(cag);
		updatedCag.setCagPatientList(getCagAllPatients(updatedCag.getId()));
		
		return updatedCag;
	}
	
	@Override
	public CagPatient getCagPatientByUuid(String uuid) {
		return dao.getCagPatientByUuid(uuid);
	}
	
	@Override
	public CagVisit openCagVisit(CagVisit cagVisit) {
		cagVisit.setCreator(Context.getAuthenticatedUser());
		cagVisit.setIsActive(true);
		// The REST payload nests each member's REG encounter inside its Visit but never sets
		// Encounter.visit, which owns encounter.visit_id: without this they are saved with no visit.
		for (Visit visit : cagVisit.getVisits()) {
			for (Encounter encounter : visit.getEncounters()) {
				if (encounter.getVisit() == null) {
					encounter.setVisit(visit);
				}
			}
		}
		CagVisit savedCagVisit = dao.saveCagVisit(cagVisit);
		
		Map<String, String> absentees = cagVisit.getAbsentees();
		
		if (!absentees.isEmpty())
			for (Map.Entry<String, String> absenteeEntry : absentees.entrySet()) {
				Absentee absentee = new Absentee();
				Patient absentPatient = Context.getPatientService().getPatientByUuid(absenteeEntry.getKey());
				absentee.setPatientId(absentPatient.getPatientId());
				absentee.setCagVisitId(savedCagVisit.getId());
				absentee.setReason(absenteeEntry.getValue());
				
				absentees.put(
				    absenteeEntry.getKey(),
				    absentPatient.getPatientIdentifier(3) + " - " + absentPatient.getGivenName() + " "
				            + absentPatient.getFamilyName() + " : " + absenteeEntry.getValue());
				dao.saveAbsentCagPatient(absentee);
			}
		
		Set<Visit> visits = cagVisit.getVisits();
		CagEncounter cagEncounter = new CagEncounter();
		cagEncounter.setCag(cagVisit.getCag());
		cagEncounter.setCagVisit(savedCagVisit);
		cagEncounter.setAttender(cagVisit.getAttender());
		cagEncounter.setCagEncounterDateTime(cagVisit.getDateStarted());
		
		for (Visit currentVisit : visits) {
			List<Encounter> visitEncounters = currentVisit.getNonVoidedEncounters();
			if (!visitEncounters.isEmpty()) {
				cagEncounter.getEncounters().add(visitEncounters.get(0));
			}
		}
		
		saveCagEncounter(cagEncounter);
		return getCagVisitByUuid(savedCagVisit.getUuid());
	}
	
	@Override
	public CagVisit getCagVisitByUuid(String uuid) {
		CagVisit retrievedCagVisit = dao.getCagVisitByUuid(uuid);
		Map<String, String> absentees = getAbsentees(retrievedCagVisit);
		retrievedCagVisit.setAbsentees(absentees);
		
		return retrievedCagVisit;
	}
	
	@Override
	public List<CagVisit> getCagAllVisits() {
		return dao.getAllCagVisits();
	}
	
	@Override
	public List<CagVisit> searchCagVisits(String attenderUuid, Boolean isActive) {
		Patient attender = Context.getPatientService().getPatientByUuid(attenderUuid);
		return getDao().searchCagVisits(attender, isActive);
	}
	
	public Map<String, String> getAbsentees(CagVisit cagVisit) {
		Map<String, String> absentees = new HashMap<String, String>();
		List<Absentee> absenteeList = dao.getAllAbsentees(cagVisit.getId());
		
		if (!absenteeList.isEmpty()) {
			
			for (Absentee absentee : absenteeList) {
				
				Patient absentPatient = Context.getPatientService().getPatient(absentee.getPatientId());
				
				absentees.put(
				    absentPatient.getUuid(),
				    absentPatient.getPatientIdentifier(3) + " - " + absentPatient.getGivenName() + " "
				            + absentPatient.getFamilyName() + " : " + absentee.getReason());
			}
		}
		return absentees;
	}
	
	@Override
	public CagVisit closeCagVisit(String uuid, String dateStopped) throws ParseException {
		
		try {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date stopDate = simpleDateFormat.parse(dateStopped);
			
			String dateTime = formatDateTime(stopDate);
			CagVisit cagVisit = dao.closeCagVisit(uuid, dateTime);
			CagVisit updatedCagVisit = getCagVisitByUuid(cagVisit.getUuid());
			
			Map<String, String> absentees = getAbsentees(updatedCagVisit);
			Set<String> absenteeUuidSet = absentees.keySet();
			Integer cagId = updatedCagVisit.getCag().getId();
			Date visitStartDate = updatedCagVisit.getDateStarted();
			
			closePatientVisits(absenteeUuidSet, cagId, visitStartDate, dateTime);
			
			return updatedCagVisit;
		}
		catch (Exception e) {
			throw new ParseException("Date ParseException encountered!", 1);
		}
		
	}
	
	public void closePatientVisits(Set<String> absenteeUuidSet, Integer cagId, Date visitDate, String dateStopped) {
		List<Patient> cagPatientList = getCagAllPatients(cagId);
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String VisitTime = simpleDateFormat.format(visitDate);
		
		for (Patient presentPatient : cagPatientList) {
			if (!absenteeUuidSet.contains(presentPatient.getUuid())) {
				dao.closeCagPatientVisit(presentPatient, VisitTime, dateStopped);
			}
		}
		
	}
	
	@Override
	public void deleteCagVisit(String uuid) {
		dao.deleteCagVisit(uuid);
	}
	
	@Override
	public List<CagEncounter> getAllCagEncounters() {
		return dao.getAllCagEncounters();
	}
	
	@Override
	public List<CagEncounter> searchCagEncounters(String cagUuid) {
		Cag cag = dao.getCagByUuid(cagUuid);
		return dao.searchCagEncounters(cag);
	}
	
	@Override
	public CagEncounter getCagEncounterByUuid(String uuid) {
		return dao.getCagEncounterByUuid(uuid);
	}
	
	@Override
	public CagEncounter saveCagEncounter(CagEncounter cagEncounter) {
		User creator = Context.getAuthenticatedUser();
		cagEncounter.setCreator(creator);
		
		if (cagEncounter.getCagVisit() == null) {
			//Corresponding cag visit with datetime 5 minutes before cag encounter creation time
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(cagEncounter.getCagEncounterDateTime());
			calendar.add(Calendar.MINUTE, -5);
			Date visitDateTime = calendar.getTime();
			
			CagVisit cagVisit = new CagVisit();
			cagVisit.setDateStarted(visitDateTime);
			cagVisit.setCreator(creator);
			cagVisit.setCag(cagEncounter.getCag());
			cagVisit.setAttender(cagEncounter.getAttender());
			cagVisit.setIsActive(true);
			cagVisit.setLocationName(cagEncounter.getLocation().getName());
			cagVisit.setDateCreated(new Date());
			CagVisit savedCagVisit = dao.saveCagVisit(cagVisit);
			
			cagEncounter.setCagVisit(savedCagVisit);
			dao.saveCagEncounter(cagEncounter);
			Set<Encounter> encounters = cagEncounter.getEncounters();
			
			Calendar calendar1 = Calendar.getInstance();
			calendar.setTime(cagEncounter.getCagEncounterDateTime());
			calendar.add(Calendar.MINUTE, 15);
			Date visitClosingDateTime = calendar.getTime();
			
			for (Encounter currentEncounter : encounters) {
				
				//Corresponding visit with datetime 5 minutes before encounter creation time
				Visit visit = new Visit(currentEncounter.getPatient(), new VisitType(10), cagVisit.getDateStarted());
				visit.setDateCreated(new Date());
				visit.setPatient(currentEncounter.getPatient());
				visit.setLocation(currentEncounter.getLocation());
				visit.setCreator(creator);
				Visit savedVisit = Context.getVisitService().saveVisit(visit);
				cagVisit.getVisits().add(savedVisit);
				
				currentEncounter.setVisit(savedVisit);
				dao.saveCagPatientEncounter(currentEncounter);
				
				Set<Obs> topLevelObs = currentEncounter.getObsAtTopLevel(false);
				for (Obs currentObs : topLevelObs) {
					currentObs.setEncounter(currentEncounter);
					Context.getObsService().saveObs(currentObs, "Saving Cag member Observation");
				}
				if (!currentEncounter.getOrders().isEmpty()) {
					Set<Order> orders = currentEncounter.getOrders();
					Object[] ordersArray = orders.toArray();
					
					for (int index = 0; index < ordersArray.length; index += 2) {
						Order currentOrder = (Order) ordersArray[index];
						currentOrder.setInstructions("As directed");
						Context.getOrderService().saveOrder(currentOrder, new OrderContext());
					}
				}
				Context.getVisitService().endVisit(savedVisit, visitClosingDateTime);
			}
			dao.saveCagVisit(cagVisit);
			String closingDate = formatDateTime(visitClosingDateTime);
			dao.closeCagVisit(savedCagVisit.getUuid(), closingDate);
		} else {
			
			dao.saveCagEncounter(cagEncounter);
			Set<Encounter> encounters = cagEncounter.getEncounters();
			
			for (Encounter currentEncounter : encounters) {
				dao.saveCagPatientEncounter(currentEncounter);
				
				Set<Obs> topLevelObs = currentEncounter.getObsAtTopLevel(false);
				for (Obs currentObs : topLevelObs) {
					currentObs.setEncounter(currentEncounter);
					Context.getObsService().saveObs(currentObs, "Saving Cag member Observation");
				}
				if (!currentEncounter.getOrders().isEmpty()) {
					Set<Order> orders = currentEncounter.getOrders();
					Object[] ordersArray = orders.toArray();
					
					for (int index = 0; index < ordersArray.length; index += 2) {
						Order currentOrder = (Order) ordersArray[index];
						currentOrder.setInstructions("As directed");
						Context.getOrderService().saveOrder(currentOrder, new OrderContext());
					}
				}
				
			}
		}
		
		return getCagEncounterByUuid(cagEncounter.getUuid());
	}
	
	@Override
	public void deleteCagEncounter(String uuid) {
		dao.deleteCagEncounter(uuid);
	}
	
	@Override
	public CagEncounter updateCagEncounter(String cagEncounterUuid, String locationUuid, Date nextEncounterDateTime) {
		Location location = dao.getLocation(locationUuid);
		Integer locationId = location.getLocationId();
		
		dao.updateCagEncounter(cagEncounterUuid, locationId, nextEncounterDateTime);
		
		return dao.getCagEncounterByUuid(cagEncounterUuid);
	}
	
	public String formatDateTime(Date inputDate) {
		SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return outputDateFormat.format(inputDate);
	}
	
}
