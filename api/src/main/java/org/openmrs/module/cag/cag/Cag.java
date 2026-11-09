package org.openmrs.module.cag.cag;

import javax.persistence.*;
import javax.validation.OverridesAttribute;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.User;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Entity(name = "cag")
@AttributeOverride(name = "creator", column = @Column(name = "created_by"))
@JsonIgnoreProperties({ "creator", "changedBy" })
public class Cag extends BaseOpenmrsData {
	
	@Id
	@GeneratedValue
	@Column(name = "cag_id")
	private Integer id;
	
	private String name;
	
	private String description;
	
	private String village;
	
	private String constituency;
	
	private String district;
	
	@Transient
	@JsonIgnore
	private List<Patient> cagPatientList;
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public List<Patient> getCagPatientList() {
		return cagPatientList;
	}
	
	public void setCagPatientList(List<Patient> cagPatientList) {
		this.cagPatientList = cagPatientList;
	}
	
	public String getDescription() {
		return description;
	}
	
	public Cag() {
	}
	
	public Cag(Integer id) {
		this.id = id;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getVillage() {
		return village;
	}
	
	public void setVillage(String village) {
		this.village = village;
	}
	
	public String getConstituency() {
		return constituency;
	}
	
	public void setConstituency(String constituency) {
		this.constituency = constituency;
	}
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public void setDistrict(String district) {
		this.district = district;
	}
	
	public String getDistrict() {
		return district;
	}
	
	@Override
	public String toString() {
		return "Cag{" + "id=" + id + ", name='" + name + '\'' + ", description='" + description + '\'' + ", village='"
		        + village + '\'' + ", constituency='" + constituency + '\'' + ", district='" + district + '\'' + '}';
	}
}
