package org.openmrs.module.cag.api;

/**
 * What the Clinical patient queue needs to know about one visit that belongs to an active CAG
 * visit: which CAG it is (for the shared colour) and who the physically present member is (only
 * that member's tile stays clickable).
 */
public class CagQueueEntry {
	
	private final String visitUuid;
	
	private final String patientUuid;
	
	private final String cagUuid;
	
	private final String cagName;
	
	private final String cagVisitUuid;
	
	private final String presentMemberUuid;
	
	private final String presentMemberName;
	
	public CagQueueEntry(String visitUuid, String patientUuid, String cagUuid, String cagName, String cagVisitUuid,
	    String presentMemberUuid, String presentMemberName) {
		this.visitUuid = visitUuid;
		this.patientUuid = patientUuid;
		this.cagUuid = cagUuid;
		this.cagName = cagName;
		this.cagVisitUuid = cagVisitUuid;
		this.presentMemberUuid = presentMemberUuid;
		this.presentMemberName = presentMemberName;
	}
	
	public String getVisitUuid() {
		return visitUuid;
	}
	
	public String getPatientUuid() {
		return patientUuid;
	}
	
	public String getCagUuid() {
		return cagUuid;
	}
	
	public String getCagName() {
		return cagName;
	}
	
	public String getCagVisitUuid() {
		return cagVisitUuid;
	}
	
	public String getPresentMemberUuid() {
		return presentMemberUuid;
	}
	
	public String getPresentMemberName() {
		return presentMemberName;
	}
	
	/** True for the member the CAG visit was started from (cag_visit.patient_id). */
	public boolean isPresentMember() {
		return presentMemberUuid != null && presentMemberUuid.equals(patientUuid);
	}
}
