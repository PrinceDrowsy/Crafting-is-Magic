package craftingismagic.cim.villager;

/**
 * The 8 Idle Chatter conversation subjects (README: Idle Chatter, in more detail). Server-only -
 * never sent over the network, just used to pick and flavor the opening line (see
 * {@link IdleChatterDialogue}).
 */
public enum IdleChatterTopic {
	WEATHER,
	WORK_CRAFT,
	VILLAGE_GOSSIP,
	HOBBIES_INTERESTS,
	CURRENT_EVENT,
	FRIENDSHIP_RELATIONSHIPS,
	CUTIE_MARK_STORY,
	HOMELAND_FAMILY
}
