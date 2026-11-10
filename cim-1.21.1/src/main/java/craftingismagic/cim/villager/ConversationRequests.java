package craftingismagic.cim.villager;

import java.util.List;
import java.util.Optional;

import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * The things a player can bring up in conversation, in place of the old interact menu (design doc:
 * Conversations replace the interact menu). Each opens a short {@code .tale} conversation under its
 * own start context, where the pony reacts in character - and may say no - and effects like
 * {@code ~ take_gift} or {@code ~ open_trade} do the actual thing. The player's words for each are
 * the {@code request.<namespace>.<path>} lang key.
 * <p>
 * Asking for rumors and offering or asking for help join when their systems (quests, favors) exist,
 * rather than sitting here as options that lead nowhere.
 */
public final class ConversationRequests {
	/**
	 * @param startContext the {@code start:} context its conversations open at
	 * @param needsItem    the player picks an item from their inventory first (it becomes {@code {gift}})
	 */
	public record Request(Identifier id, String startContext, boolean needsItem) {
	}

	public static final String GIFT = "gift";
	public static final String TRADE = "trade";
	/** Making amends (design doc: Harm & forgiveness). */
	public static final String APOLOGY = "apology";

	/** "See you later." - not a request in the list, but sent the same way: the pony says goodbye, then it closes. */
	public static final Identifier FAREWELL = Identifier.of(Cim.MOD_ID, "farewell");

	public static final List<Request> ALL = List.of(
			new Request(Identifier.of(Cim.MOD_ID, GIFT), GIFT, true),
			new Request(Identifier.of(Cim.MOD_ID, TRADE), TRADE, false),
			new Request(Identifier.of(Cim.MOD_ID, APOLOGY), APOLOGY, false));

	private ConversationRequests() {
	}

	public static Optional<Request> get(Identifier id) {
		return ALL.stream().filter(request -> request.id().equals(id)).findFirst();
	}
}
