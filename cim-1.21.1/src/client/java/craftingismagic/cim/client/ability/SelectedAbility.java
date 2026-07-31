package craftingismagic.cim.client.ability;

/** The ability the radial menu last selected, persisting until changed. */
public final class SelectedAbility {
	private static Ability current = null;

	private SelectedAbility() {
	}

	public static Ability current() {
		return current;
	}

	public static void set(Ability ability) {
		current = ability;
	}

	public static boolean isSelected(Ability ability) {
		return current == ability;
	}
}
