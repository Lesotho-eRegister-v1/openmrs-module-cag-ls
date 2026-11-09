/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.cag.cag;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;
import org.openmrs.User;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import javax.persistence.*;
import java.util.List;

/**
 * Please note that a corresponding table schema must be created in liquibase.xml.
 */

@JsonIgnoreProperties({ "uuid" })
@Repository
@Entity(name = "cag_patient")
@Table(name = "cag_patient")
public class CagPatient {
	
	@Id
	@GeneratedValue
	@Column(name = "cag_patient_id")
	private Integer cagPatientId;
	
	@Column(name = "uuid")
	private String uuid;
	
	@Transient
	private String cagUuid;
	
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "cag_id")
	private Cag cag;
	
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "patient_id")
	private Patient patient;
	
	@Basic
	@Column(name = "status")
	private boolean status;
	
	@Transient
	private List<CagVisit> activeCagVisits;
	
	public void setUuid(String patientUuid) {
		this.uuid = patientUuid;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public String getCagUuid() {
		return cagUuid;
	}
	
	public void setCagUuid(String cagUuid) {
		this.cagUuid = cagUuid;
	}
	
	public void setCagPatientId(Integer cagPatientId) {
		this.cagPatientId = cagPatientId;
	}
	
	public Integer getCagPatientId() {
		return cagPatientId;
	}
	
	public Patient getPatient() {
		return patient;
	}
	
	public void setPatient(Patient patient) {
		this.patient = patient;
	}
	
	public Cag getCag() {
		return cag;
	}
	
	public void setCag(Cag cag) {
		this.cag = cag;
	}
	
	public void setStatus(boolean status) {
		this.status = status;
	}
	
	public boolean getStatus() {
		return status;
	}
	
	public List<CagVisit> getActiveCagVisits() {
		return activeCagVisits;
	}
	
	public void setActiveCagVisits(List<CagVisit> activeCagVisits) {
		this.activeCagVisits = activeCagVisits;
	}
	
	@Override
	public String toString() {
		return "CagPatient{" + "cagPatientId=" + cagPatientId + ", cag=" + cag + ", patient=" + patient + ", status="
		        + status + ",uuid=" + uuid + '}';
	}
}
