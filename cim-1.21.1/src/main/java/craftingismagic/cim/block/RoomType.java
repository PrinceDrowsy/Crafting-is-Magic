package craftingismagic.cim.block;

import java.util.Locale;

/** The kinds of room a house shell can mark - each furnished from room sets of the same kind. */
public enum RoomType {
	BEDROOM,
	KITCHEN,
	LIVING_ROOM,
	WORKROOM,
	/** A foal's room - furnished only when the household has foals; otherwise a plain spare room. */
	NURSERY;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static RoomType of(String id) {
		return valueOf(id.toUpperCase(Locale.ROOT));
	}
}
