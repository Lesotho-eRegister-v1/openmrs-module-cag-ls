package org.openmrs.module.cag;

/**
 * Global property names and their defaults for mapping the CAG attender's follow-up form onto the
 * other present members. The defaults reproduce what the Bahmni 0.92 Clinical app sent in POST
 * cagEncounter (consultationController.js createCagEncounter).
 */
public final class CagConstants {
	
	private CagConstants() {
	}
	
	public static final String GP_PROPAGATION_ENABLED = "cag.propagation.enabled";
	
	/** Comma-separated encounter type uuids whose saves are propagated. Default: Consultation. */
	public static final String GP_ENCOUNTER_TYPE_UUIDS = "cag.propagation.encounterTypeUuids";
	
	public static final String DEFAULT_ENCOUNTER_TYPE_UUIDS = "81852aee-3f10-11e4-adec-0800271c1b75";
	
	/** Top-level obs (form) that is mapped. Default: HIV Treatment and Care Progress Template. */
	public static final String GP_FORM_CONCEPT_UUID = "cag.propagation.formConceptUuid";
	
	public static final String DEFAULT_FORM_CONCEPT_UUID = "746818ac-65a0-4d74-9609-ddb2c330a31b";
	
	/**
	 * Comma-separated question concepts whose answers are copied from the attender as-is. Default:
	 * ARV drugs No. of days dispensed, HIVTC ARV drugs supply duration, Appointment scheduled, ART
	 * Follow-up date.
	 */
	public static final String GP_COPIED_CONCEPT_UUIDS = "cag.propagation.copiedConceptUuids";
	
	public static final String DEFAULT_COPIED_CONCEPT_UUIDS = "27d55083-5e66-4b5a-91d3-2c9a42cc9996,"
	        + "9eb00622-1078-4f7b-aa69-61e6c36db347,ed064424-0331-47f6-9532-77156f40a014,"
	        + "88489023-783b-4021-b7a9-05ca9877bf67";
	
	/**
	 * Question answered with each member's own latest value, never the attender's. Default: HIVTC,
	 * ART Regimen.
	 */
	public static final String GP_OWN_REGIMEN_CONCEPT_UUID = "cag.propagation.ownRegimenConceptUuid";
	
	public static final String DEFAULT_OWN_REGIMEN_CONCEPT_UUID = "13382e01-9f18-488b-b2d2-58ab54c82d82";
	
	/** Question set to the "buddy" answer on members' copies. Default: Type of client. */
	public static final String GP_CLIENT_TYPE_CONCEPT_UUID = "cag.propagation.clientTypeConceptUuid";
	
	public static final String DEFAULT_CLIENT_TYPE_CONCEPT_UUID = "e0bc761d-ac3b-4033-92c7-476304b9c5e8";
	
	public static final String GP_CLIENT_TYPE_BUDDY_ANSWER_UUID = "cag.propagation.clientTypeBuddyAnswerUuid";
	
	public static final String DEFAULT_CLIENT_TYPE_BUDDY_ANSWER_UUID = "0f880c52-3ced-43ac-a79b-07a2740ae428";
	
	/** Stored as cag_encounter.next_encounter_date. Default: ART, Follow-up date. */
	public static final String GP_FOLLOW_UP_DATE_CONCEPT_UUID = "cag.propagation.followUpDateConceptUuid";
	
	public static final String DEFAULT_FOLLOW_UP_DATE_CONCEPT_UUID = "88489023-783b-4021-b7a9-05ca9877bf67";
}
