package craftingismagic.cim.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;

import craftingismagic.cim.client.accessibility.CimText;
import craftingismagic.cim.client.dialogue.Bubbly;
import craftingismagic.cim.client.dialogue.DialogueConfig;
import craftingismagic.cim.client.dialogue.SpeechSounds;
import craftingismagic.cim.client.dialogue.VoiceStyle;
import craftingismagic.cim.network.DialogueBeatS2CPayload;
import craftingismagic.cim.network.DialogueBeatS2CPayload.LineData;
import craftingismagic.cim.network.DialogueBeatS2CPayload.OptionData;
import craftingismagic.cim.network.DialogueBeatS2CPayload.RequestData;
import craftingismagic.cim.network.DialogueBringUpC2SPayload;
import craftingismagic.cim.network.DialogueFollowUpC2SPayload;
import craftingismagic.cim.network.DialogueChooseC2SPayload;
import craftingismagic.cim.network.StopVillagerInteractionC2SPayload;
import craftingismagic.cim.speech.SpeakingVoice;
import craftingismagic.cim.villager.ConversationRequests;
import craftingismagic.cim.stamina.StaminaColorAttachment;

/**
 * The conversation screen (docs/implementation-plan.md: Dialogue screen rework) - a scrolling log in
 * the spirit of Disco Elysium, in the mod's cute-and-bubbly Mane Six style. The live pony portrait
 * sits on the left; on the right, a tall panel keeps the whole conversation so far, each line under a
 * little name tag (the pony, you, or an inner voice in its rainbow color and symbol), with the replies
 * as rounded buttons that grow to fit their words.
 * <p>
 * New lines type themselves out (speed and sound in the Conversations settings); replies appear once
 * they finish, so nothing is clicked by accident. Space or a click shows the rest at once, number keys
 * pick replies, the mouse wheel scrolls back, Esc closes.
 * <p>
 * The portrait is a real live render via {@code InventoryScreen.drawEntity} - generic over any
 * {@code LivingEntity}, through the same renderer map {@code CimVillagerPonyRenderer} is registered
 * into, so it shows the pony as they really look. Gaze tracking is internal to that method; passing the
 * cursor position each frame is all it needs.
 */
public class DialogueScreen extends Screen {
	private static final int MARGIN = 10;
	private static final int PAD = 8;
	private static final int GAP = 6;
	private static final int OPTION_GAP = 4;
	private static final int BADGE = 12;
	private static final int CLOSE = -1;
	/** The "Bring something up…" tab. */
	private static final int BRING_UP = -3;
	/** Item cells in the gift picker are {@code ITEM - slot}. */
	private static final int ITEM = -1000;
	private static final int ITEM_CELL = 22;

	/** What the reply area shows: the pony's replies, the things you can bring up, or your inventory for a gift. */
	private enum Mode { REPLIES, BRING_UP, PICK_ITEM }

	/** One entry in the log. {@code badge} entries are a voice reply's result, centered. */
	private record Entry(Text tag, int tagColor, Text body, boolean badge, int sound, String voice) {
	}

	private record Hit(int x1, int y1, int x2, int y2, int option) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private static final int SOUND_NONE = 0;
	private static final int SOUND_SPOKEN = 1;
	/** {@code *yawns* rest of the line} - a stage direction leading the spoken words. */
	private static final Pattern ACTION = Pattern.compile("^\\*([^*]+)\\*\\s*(.*)$");
	private static final Style STAGE_DIRECTION = Style.EMPTY.withItalic(true).withColor(0xC9B8E0);
	private static final int SOUND_VOICE = 2;
	private static final int SOUND_SUCCESS = 3;
	private static final int SOUND_FAILURE = 4;

	private final int entityId;
	private final String partnerName;
	private final List<Entry> entries = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private List<OptionData> options = List.of();
	private boolean ended;
	/** The things you can bring up (README: Conversations replace the interact menu). */
	private List<RequestData> requests = List.of();
	private Mode mode = Mode.REPLIES;
	private RequestData picking;
	/** An action waiting for the lines to be read (the trade screen), and how long they've been on screen. */
	private boolean followUpPending;
	/** The pony's goodbye: the screen closes itself once it's been read. */
	private boolean closingAfter;
	private float followUpTimer;
	private static final float FOLLOW_UP_DELAY = 0.9f;
	private static final float CLOSE_DELAY = 1.6f;
	/** How the pony and you sound (README: Speaking voices); null until the first beat arrives. */
	private SpeakingVoice partnerVoice;
	private SpeakingVoice yourVoice;
	/** Your reply's quick murmur: blips still to play, and time until the next. */
	private int yourBlipsLeft;
	private float yourBlipWait;
	/** For a melodic voice: where the word being sung falls in its sentence. */
	private SpeechSounds.Cadence cadence = SpeechSounds.Cadence.NONE;
	/** Seconds left before the answer to your reply starts showing. */
	private float breather;
	private static final float MIN_BREATHER = 0.55f;
	private static final float BREATHER_PER_LETTER = 0.025f;
	private static final float MAX_BREATHER = 1.8f;
	private boolean waiting;
	/** Entries fully shown; the one after them is typing out. */
	private int revealed;
	private float revealChars;
	private int sounded = -1;
	/** Faster text speaks every few letters instead, keeping the rhythm even. */
	private static final int MAX_BLIPS_PER_SECOND = 40;
	private final java.util.Random blipRandom = new java.util.Random();
	private int blipCount;
	/** Typing time not yet spent, and the breath still owed after the last letter. */
	private float timeBank;
	private float pause;
	/** Pauses are tuned at this speed and scale with the Text Reveal setting. */
	private static final int NORMAL_SPEED = 35;
	/** Pixels scrolled back from the newest line. */
	private int scroll;
	/** Pixels the replies are scrolled up, when there are more than fit; and where they are on screen. */
	private int optionScroll;
	private Hit optionsArea;
	private long lastFrame = System.nanoTime();
	private float time;

	public DialogueScreen(DialogueBeatS2CPayload beat) {
		super(Text.translatable("screen.cim.idle_chatter.title", beat.partnerName()));
		this.entityId = beat.entityId();
		this.partnerName = beat.partnerName();
		apply(beat);
	}

	public int entityId() {
		return entityId;
	}

	/** Called by the client network receiver when the server sends the next beat. */
	public void showBeat(DialogueBeatS2CPayload beat) {
		apply(beat);
	}

	private void apply(DialogueBeatS2CPayload beat) {
		this.partnerVoice = beat.scene().partner();
		this.yourVoice = beat.scene().you();
		this.requests = beat.scene().requests();
		this.closingAfter = beat.scene().followUp().equals("close");
		this.followUpPending = !beat.scene().followUp().isEmpty();
		this.followUpTimer = 0;
		this.mode = Mode.REPLIES;
		int voiceLines = 0;
		int maxVoiceLines = DialogueConfig.get().maxVoiceLines();
		for (LineData line : beat.lines()) {
			if (line.kind() == DialogueBeatS2CPayload.VOICE && ++voiceLines > maxVoiceLines) {
				continue;
			}
			entries.add(entry(line));
		}
		this.options = beat.options();
		this.ended = beat.ended() || beat.options().isEmpty();
		this.waiting = false;
		this.scroll = 0;
		this.optionScroll = Integer.MAX_VALUE; // a new set of replies starts at its first one (clamped when drawn)
	}

	private static Entry entry(LineData line) {
		if (line.kind() == DialogueBeatS2CPayload.SPOKEN) {
			String speaker = line.speaker().isEmpty() ? "..." : line.speaker();
			return new Entry(Text.literal(speaker), Bubbly.PONY_TAG, spoken(line.text()), false, SOUND_SPOKEN, "");
		}
		VoiceStyle style = VoiceStyle.of(line.voice());
		if (line.kind() == DialogueBeatS2CPayload.VOICE) {
			return new Entry(plainLabel(style), Bubbly.opaque(style.color), style.colored(line.text()), false, SOUND_VOICE, style.voice);
		}
		boolean success = line.kind() == DialogueBeatS2CPayload.SUCCESS;
		String key = "screen.cim.dialogue." + (style.named() ? "" : "whisper_") + (success ? "success" : "failure");
		MutableText result = plainLabel(style).append(style.named() ? " - " : " ").append(Text.translatable(key));
		if (success) {
			result = Text.literal("★ ").append(result).append(" ★"); // not ✦ - that is Magic's own symbol
		}
		int color = success ? Bubbly.opaque(style.color) : Bubbly.darken(Bubbly.opaque(style.color), 0.35f);
		return new Entry(null, color, result, true, success ? SOUND_SUCCESS : SOUND_FAILURE, style.voice);
	}

	/**
	 * Spoken words go in quotes, so they never read like an inner voice's thoughts. A leading
	 * {@code *action*} is a stage direction: italic and outside the quotes - {@code *yawns* “Hi.”}.
	 */
	private static Text spoken(String text) {
		Matcher action = ACTION.matcher(text);
		if (!action.matches()) {
			return Text.literal("“" + text + "”");
		}
		MutableText result = Text.empty().append(Text.literal("*" + action.group(1).trim() + "*").setStyle(STAGE_DIRECTION));
		String words = action.group(2).trim();
		return words.isEmpty() ? result : result.append(Text.literal(" “" + words + "”"));
	}

	/** "✿ Kindness" without color, for drawing on a pill of that color. */
	private static MutableText plainLabel(VoiceStyle style) {
		MutableText label = Text.literal(style.symbol);
		if (style.named()) {
			label.append(" ").append(Text.translatable("voice.cim." + style.voice));
		}
		return label;
	}

	// ---- Choosing ----

	/** Whether the lines are all shown and it's the player's turn - nothing pending, nothing typing. */
	private boolean playersTurn() {
		return !waiting && !followUpPending && revealed >= entries.size();
	}

	/**
	 * What the reply area offers right now: the pony's replies; or, while bringing something up (and
	 * whenever a conversation has run its course), the requests plus "Never mind" / "See you later".
	 */
	private List<OptionData> choices() {
		if (!playersTurn() || mode == Mode.PICK_ITEM) {
			return List.of();
		}
		if (mode == Mode.BRING_UP || ended) {
			if (requests.isEmpty()) {
				return List.of();
			}
			List<OptionData> list = new ArrayList<>();
			for (RequestData request : requests) {
				list.add(new OptionData("", "", requestLine(request).getString(), -1));
			}
			list.add(new OptionData("", "", Text.translatable(ended ? "screen.cim.dialogue.goodbye" : "screen.cim.dialogue.never_mind").getString(), -1));
			return list;
		}
		return options;
	}

	private boolean optionsShowing() {
		return !choices().isEmpty();
	}

	private static Text requestLine(RequestData request) {
		return Text.translatable("request." + request.id().getNamespace() + "." + request.id().getPath());
	}

	private void choose(int index) {
		List<OptionData> list = choices();
		if (index < 0 || index >= list.size()) {
			return;
		}
		if (mode == Mode.BRING_UP || ended) {
			if (index >= requests.size()) {
				// "Never mind" goes back to the conversation; "See you later" gets the pony's goodbye, then ends it.
				if (ended) {
					sayGoodbye();
				} else {
					mode = Mode.REPLIES;
				}
				return;
			}
			RequestData request = requests.get(index);
			if (request.needsItem()) {
				picking = request;
				mode = Mode.PICK_ITEM;
			} else {
				bringUp(request, -1, requestLine(request));
			}
			return;
		}
		OptionData option = options.get(index);
		say(yourLine(option), option.text());
		ClientPlayNetworking.send(new DialogueChooseC2SPayload(entityId, index));
	}

	/** You bring {@code request} up - with the item from {@code slot}, for a gift. */
	private void bringUp(RequestData request, int slot, Text words) {
		say(yourEntry(words), words.getString());
		ended = false;
		mode = Mode.REPLIES;
		ClientPlayNetworking.send(new DialogueBringUpC2SPayload(entityId, request.id(), slot));
	}

	/** "See you later." - the pony answers with their goodbye, and the screen closes once it's read. */
	private void sayGoodbye() {
		Text words = Text.translatable("screen.cim.dialogue.goodbye");
		say(yourEntry(words), words.getString());
		ended = false;
		mode = Mode.REPLIES;
		ClientPlayNetworking.send(new DialogueBringUpC2SPayload(entityId, ConversationRequests.FAREWELL, -1));
	}

	/** Puts your line in the log, murmured in your voice, then waits for the pony - with a breath between. */
	private void say(Entry line, String words) {
		entries.add(line);
		// Your line shows at once, with a quick murmur in your own voice - about a blip per three letters.
		int letters = (int) words.chars().filter(Character::isLetterOrDigit).count();
		yourBlipsLeft = letters == 0 ? 1 : Math.min(8, Math.max(2, letters / 3));
		yourBlipWait = 0;
		// Breathing room: the pony lets your words land before answering - longer replies, a longer beat.
		breather = Math.min(MAX_BREATHER, MIN_BREATHER + letters * BREATHER_PER_LETTER);
		revealed = entries.size();
		revealChars = 0;
		waiting = true;
		options = List.of();
		scroll = 0;
	}

	/** The items you could give: one cell per kind of item in your inventory, at the first slot holding it. */
	private List<Integer> giftSlots() {
		List<Integer> slots = new ArrayList<>();
		if (this.client == null || this.client.player == null) {
			return slots;
		}
		List<net.minecraft.item.ItemStack> main = this.client.player.getInventory().main;
		java.util.Set<net.minecraft.item.Item> seen = new java.util.HashSet<>();
		for (int slot = 0; slot < main.size(); slot++) {
			if (!main.get(slot).isEmpty() && seen.add(main.get(slot).getItem())) {
				slots.add(slot);
			}
		}
		return slots;
	}

	private void pickGift(int slot) {
		if (picking == null || this.client == null || this.client.player == null) {
			return;
		}
		Text words = Text.translatable("request.cim.gift.with", this.client.player.getInventory().main.get(slot).getName());
		bringUp(picking, slot, words);
		picking = null;
	}

	/** Something you said, as your own line in the log, tagged in your Stamina color. */
	private Entry yourEntry(Text body) {
		int color = 0xFFB9A5E8;
		if (this.client != null && this.client.player != null) {
			color = Bubbly.opaque(StaminaColorAttachment.getColors(this.client.player).fillColor());
		}
		return new Entry(Text.translatable("screen.cim.dialogue.you"), color, body, false, SOUND_NONE, "");
	}

	/** What you picked, as your own line in the log, tagged in your Stamina color. */
	private Entry yourLine(OptionData option) {
		int color = 0xFFB9A5E8;
		if (this.client != null && this.client.player != null) {
			color = Bubbly.opaque(StaminaColorAttachment.getColors(this.client.player).fillColor());
		}
		Text body;
		if (!option.voice().isEmpty()) {
			VoiceStyle style = VoiceStyle.of(option.voice());
			body = Text.literal(style.symbol + " ").styled(s -> s.withColor(style.color)).append(Text.literal(option.text()));
		} else if (!option.text().isEmpty()) {
			body = Text.literal(option.text());
		} else {
			body = Text.translatable("screen.cim.idle_chatter." + option.tone()).formatted(Formatting.GRAY);
		}
		return new Entry(Text.translatable("screen.cim.dialogue.you"), color, body, false, SOUND_NONE, "");
	}

	private void finishReveal() {
		breather = 0;
		revealed = entries.size();
		revealChars = 0;
		timeBank = 0;
		pause = 0;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (revealed < entries.size()) {
			finishReveal();
			return true;
		}
		for (Hit hit : hits) {
			if (hit.contains(mouseX, mouseY)) {
				if (hit.option() == CLOSE) {
					close();
				} else if (hit.option() == BRING_UP) {
					mode = Mode.BRING_UP;
				} else if (hit.option() <= ITEM) {
					pickGift(ITEM - hit.option());
				} else {
					choose(hit.option());
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
			int index = keyCode - GLFW.GLFW_KEY_1;
			if (mode == Mode.PICK_ITEM) {
				List<Integer> slots = giftSlots();
				if (index < slots.size()) {
					pickGift(slots.get(index));
				}
			} else {
				choose(index);
			}
			return true;
		}
		// Esc steps back out of picking a gift or bringing something up before it closes the conversation.
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && mode != Mode.REPLIES) {
			mode = mode == Mode.PICK_ITEM ? Mode.BRING_UP : Mode.REPLIES;
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
			if (revealed < entries.size()) {
				finishReveal();
			} else if (ended && playersTurn()) {
				sayGoodbye();
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int amount = (int) Math.round(verticalAmount * lineHeight() * 2);
		if (optionsArea != null && optionsArea.contains(mouseX, mouseY)) {
			optionScroll = Math.max(0, optionScroll + amount);
		} else {
			scroll = Math.max(0, scroll + amount);
		}
		return true;
	}

	// ---- Typing out ----

	private void advanceReveal(float seconds) {
		if (breather > 0) {
			breather -= seconds; // counts down even while the answer is still on its way
			return;
		}
		if (revealed >= entries.size()) {
			return;
		}
		Entry current = entries.get(revealed);
		if (current.badge()) {
			if (sounded < revealed) {
				sounded = revealed;
				playResult(current);
			}
			revealed++;
			return;
		}
		int speed = DialogueConfig.get().revealSpeed().charsPerSecond;
		if (speed <= 0) {
			// Nothing types out, so each line gets a single blip.
			if (sounded < revealed) {
				sounded = revealed;
				blip(current);
			}
			finishReveal();
			return;
		}
		String text = current.body().getString();
		if (current.sound() == SOUND_VOICE) {
			speed = Math.max(1, Math.round(speed * VoiceStyle.of(current.voice()).pace));
		} else if (current.sound() == SOUND_SPOKEN && partnerVoice != null && current.tag().getString().equals(partnerName)) {
			// Some speak slower than others - Celestia takes her time, as if imparting grand wisdom.
			speed = Math.max(1, Math.round(speed * partnerVoice.pace()));
		}
		// Letter by letter on a clock, with a breath after spaces and punctuation, so text and blips
		// share one rhythm and sound like speech rather than a stream of noise.
		float perLetter = 1f / speed;
		float pauseScale = NORMAL_SPEED / (float) speed;
		timeBank += seconds;
		boolean speak = false;
		while (timeBank > 0 && revealChars < text.length()) {
			if (pause > 0) {
				float wait = Math.min(pause, timeBank);
				pause -= wait;
				timeBank -= wait;
				continue;
			}
			if (timeBank < perLetter) {
				break;
			}
			timeBank -= perLetter;
			int index = (int) revealChars;
			char c = text.charAt(index);
			revealChars = index + 1;
			if (melodicPartner(current)) {
				// A melodic voice sings one note per word - a tune, not a flurry - and its sentences resolve.
				if (Character.isLetterOrDigit(c) && (index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1)))) {
					speak = true;
					cadence = cadenceAfterWord(text, index);
				}
			} else {
				speak |= talks(current, c, speed);
			}
			pause = pauseAfter(c, index + 1 < text.length() ? text.charAt(index + 1) : ' ') * pauseScale;
		}
		if (speak) {
			blip(current); // several letters in one frame still make one blip
		}
		if (revealChars >= text.length()) {
			// The pause after the last period carries into the next line, so speakers don't run together.
			revealed++;
			revealChars = 0;
			blipCount = 0;
		}
	}

	/** Whether {@code entry} is the pony speaking in a melodic voice (a princess's). */
	private boolean melodicPartner(Entry entry) {
		return entry.sound() == SOUND_SPOKEN && partnerVoice != null && partnerVoice.melodic()
				&& entry.tag().getString().equals(partnerName);
	}

	/** Whether the word starting at {@code start} ends its sentence, pauses at a comma, or runs on. */
	private static SpeechSounds.Cadence cadenceAfterWord(String text, int start) {
		int end = start;
		while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '\'')) {
			end++;
		}
		// Skip a closing quote or stage-direction star to the punctuation behind it.
		while (end < text.length() && "\"”*)".indexOf(text.charAt(end)) >= 0) {
			end++;
		}
		if (end >= text.length()) {
			return SpeechSounds.Cadence.FULL;
		}
		return switch (text.charAt(end)) {
			case '.', '!', '?', '…' -> SpeechSounds.Cadence.FULL;
			case ',', ';', ':', '-', '—', '–' -> SpeechSounds.Cadence.HALF;
			default -> SpeechSounds.Cadence.NONE;
		};
	}

	/** The breath after {@code c}, in seconds at normal speed. Dots in an ellipsis trail off rather than stall. */
	private static float pauseAfter(char c, char next) {
		return switch (c) {
			case ' ' -> 0.04f;
			case ',', '-', '—', '–' -> 0.14f;
			case ';', ':' -> 0.2f;
			case '.' -> next == '.' ? 0.12f : 0.32f;
			case '!', '?' -> 0.32f;
			case '…' -> 0.36f;
			default -> 0f;
		};
	}

	/**
	 * Undertale-style speech: whether this letter blips. Blips land on the letters themselves, so the
	 * sound keeps exactly the typewriter's pace - spaces and punctuation stay quiet (natural pauses),
	 * and at fast speeds every second or third letter speaks so it never blurs into a buzz.
	 */
	private boolean talks(Entry entry, char c, int charsPerSecond) {
		if (!Character.isLetterOrDigit(c)) {
			return false;
		}
		int voiceEvery = entry.sound() == SOUND_VOICE ? VoiceStyle.of(entry.voice()).blipEvery : 1;
		int every = Math.max(voiceEvery, (int) Math.ceil(charsPerSecond / (double) MAX_BLIPS_PER_SECOND));
		return blipCount++ % every == 0;
	}

	/**
	 * One spoken blip, wobbling a touch in pitch so it sounds like talking. Each pony speaks in their
	 * own voice (README: Speaking voices) and each inner voice with its instrument; your own reply
	 * gets a quick murmur in your voice instead ({@link #speakYourLine}).
	 */
	private void blip(Entry entry) {
		if (this.client == null || !DialogueConfig.get().revealSound || entry.sound() == SOUND_NONE) {
			return;
		}
		float wobble = 0.95f + blipRandom.nextFloat() * 0.1f;
		if (entry.sound() == SOUND_VOICE) {
			VoiceStyle.of(entry.voice()).blip(this.client, wobble);
		} else if (partnerVoice != null && entry.tag().getString().equals(partnerName)) {
			SpeechSounds.blip(this.client, partnerVoice, wobble, 0.25f, cadence);
			cadence = SpeechSounds.Cadence.NONE;
		} else {
			// Someone else in the scene, with no voice of their own yet: the harp at a pitch from their name.
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(),
					ponyPitch(entry.tag().getString()) * wobble, 0.25f));
		}
	}

	/** Your reply's quick murmur in your own voice - unless player voices are switched off. */
	private void speakYourLine(float seconds) {
		if (yourBlipsLeft <= 0 || this.client == null || yourVoice == null) {
			return;
		}
		if (!DialogueConfig.get().playerVoices) {
			yourBlipsLeft = 0;
			return;
		}
		yourBlipWait -= seconds;
		if (yourBlipWait <= 0) {
			SpeechSounds.blip(this.client, yourVoice, 0.95f + blipRandom.nextFloat() * 0.1f, 0.22f);
			yourBlipsLeft--;
			yourBlipWait = 0.07f + (blipRandom.nextInt(4) == 0 ? 0.08f : 0f);
		}
	}

	/** A pony's speaking pitch, from their name: 0.75-1.5. */
	private static float ponyPitch(String name) {
		return 0.75f + Math.floorMod(name.hashCode(), 76) / 100f;
	}

	/** A voice reply's result: its voice, higher when it landed, lower when it missed. */
	private void playResult(Entry entry) {
		if (this.client != null && DialogueConfig.get().revealSound) {
			VoiceStyle.of(entry.voice()).play(this.client, entry.sound() == SOUND_SUCCESS ? 1.35f : 0.6f);
		}
	}

	// ---- Drawing ----

	private float scale() {
		return CimText.scale();
	}

	private int lineHeight() {
		return CimText.lineHeight(this.textRenderer);
	}

	private List<OrderedText> wrap(Text text, int width) {
		return CimText.wrap(this.textRenderer, text, width);
	}

	/** On the screen's own bubbles and buttons, so never over the chosen backdrop - a dark one would fight their ink. */
	private void drawLines(DrawContext context, List<OrderedText> lines, int x, int y, int color) {
		CimText.drawLines(context, this.textRenderer, lines, x, y, color, false, false);
	}

	/** How wide a line of reading text is on screen. */
	private int readingWidth(Text text) {
		return CimText.width(this.textRenderer, CimText.style(text).asOrderedText());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float seconds = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		time += seconds * 20f;
		advanceReveal(seconds);
		speakYourLine(seconds);
		SpeechSounds.flush(); // a melodic voice's layers ring a few milliseconds after each note
		// "Sure, take a look!" - then, once it's had a moment to be read, the trade screen.
		if (followUpPending && !waiting && breather <= 0 && revealed >= entries.size()) {
			followUpTimer += seconds;
			if (closingAfter && followUpTimer >= CLOSE_DELAY) {
				followUpPending = false;
				close();
				return;
			}
			if (!closingAfter && followUpTimer >= FOLLOW_UP_DELAY) {
				followUpPending = false;
				ClientPlayNetworking.send(new DialogueFollowUpC2SPayload(entityId));
			}
		}

		super.render(context, mouseX, mouseY, delta);
		hits.clear();

		int leftWidth = Math.max(100, Math.min(180, (int) (this.width * 0.3f)));
		drawPortrait(context, mouseX, mouseY, leftWidth);

		// One batch for the whole panel - drawn piece by piece, every fill would flush to the GPU alone.
		context.draw(() -> drawPanel(context, mouseX, mouseY, leftWidth));
	}

	private void drawPanel(DrawContext context, int mouseX, int mouseY, int leftWidth) {
		int panelX = MARGIN * 2 + leftWidth;
		int panelY = MARGIN;
		int panelWidth = this.width - panelX - MARGIN;
		int panelHeight = this.height - MARGIN * 2;
		Bubbly.framed(context, panelX, panelY, panelWidth, panelHeight, 8, Bubbly.ACCENT, Bubbly.PANEL_TOP, Bubbly.PANEL_BOTTOM);
		Bubbly.sparkle(context, panelX + 6, panelY + 6, time, 0f);
		Bubbly.sparkle(context, panelX + panelWidth - 7, panelY + 6, time, 2.1f);
		Bubbly.sparkle(context, panelX + panelWidth - 7, panelY + panelHeight - 7, time, 4.2f);

		int innerX = panelX + PAD;
		int innerWidth = panelWidth - PAD * 2;
		int innerTop = panelY + PAD;
		int innerBottom = panelY + panelHeight - PAD;

		int buttonsTop = drawButtons(context, mouseX, mouseY, innerX, innerWidth, innerTop, innerBottom);
		int logBottom = buttonsTop < innerBottom ? buttonsTop - GAP : innerBottom;
		drawLog(context, innerX, innerWidth, innerTop, logBottom);

		if (waiting) {
			drawLines(context, wrap(Text.literal("…"), innerWidth), innerX + innerWidth / 2 - 4, innerBottom - lineHeight(), 0xFFCC8CF2);
		}
	}

	private void drawPortrait(DrawContext context, int mouseX, int mouseY, int leftWidth) {
		int x1 = MARGIN;
		int x2 = MARGIN + leftWidth;
		int plaqueHeight = Bubbly.pillHeight(this.textRenderer, scale());
		int y1 = MARGIN + 10;
		int y2 = this.height - MARGIN - plaqueHeight - 16;
		if (this.client != null && this.client.world != null
				&& this.client.world.getEntityById(entityId) instanceof LivingEntity living) {
			int size = Math.max(20, Math.min(leftWidth, y2 - y1) / 3);
			// The game draws a name tag over a named mob the crosshair is on (the render dispatcher keeps
			// its own copy of that), or always if its name is set to show - the plaque below names them
			// already, so hide both just for the portrait.
			EntityRenderDispatcher dispatcher = this.client.getEntityRenderDispatcher();
			Entity targeted = dispatcher.targetedEntity;
			boolean nameShown = living.isCustomNameVisible();
			dispatcher.targetedEntity = null;
			living.setCustomNameVisible(false);
			try {
				InventoryScreen.drawEntity(context, x1, y1, x2, y2, size, 0.0625f, mouseX, mouseY, living);
			} finally {
				dispatcher.targetedEntity = targeted;
				living.setCustomNameVisible(nameShown);
			}
		}
		Text name = CimText.style(Text.literal(partnerName));
		int nameWidth = Math.round(this.textRenderer.getWidth(name) * scale()) + 8;
		int plaqueX = x1 + (leftWidth - nameWidth) / 2;
		int plaqueY = y2 + 8;
		Bubbly.pill(context, this.textRenderer, name, plaqueX, plaqueY, scale(), Bubbly.PONY_TAG, Bubbly.INK);
		Bubbly.sparkle(context, plaqueX - 5, plaqueY + plaqueHeight / 2, time, 1f);
		Bubbly.sparkle(context, plaqueX + nameWidth + 4, plaqueY + plaqueHeight / 2, time, 3f);
	}

	/** Draws the log between {@code top} and {@code bottom}, newest at the bottom, clipped and scrollable. */
	private void drawLog(DrawContext context, int x, int width, int top, int bottom) {
		int lineHeight = lineHeight();
		int pillHeight = Bubbly.pillHeight(this.textRenderer, scale());
		int shown = Math.min(entries.size(), revealed + 1);

		List<List<OrderedText>> bodies = new ArrayList<>();
		int total = 0;
		for (int i = 0; i < shown; i++) {
			Entry entry = entries.get(i);
			Text body = i < revealed ? entry.body() : truncate(entry.body(), (int) revealChars);
			if (entry.badge()) {
				bodies.add(List.of());
				total += pillHeight + GAP;
			} else {
				List<OrderedText> lines = wrap(body, width);
				bodies.add(lines);
				total += pillHeight + 2 + lines.size() * lineHeight + GAP;
			}
		}
		int maxScroll = Math.max(0, total - (bottom - top));
		scroll = Math.min(scroll, maxScroll);

		context.enableScissor(x - 2, top, x + width + 2, bottom);
		int y = bottom - total + scroll;
		for (int i = 0; i < shown; i++) {
			Entry entry = entries.get(i);
			if (entry.badge()) {
				Text text = CimText.style(entry.body());
				int badgeWidth = Math.round(this.textRenderer.getWidth(text) * scale()) + 8;
				Bubbly.pill(context, this.textRenderer, text, x + (width - badgeWidth) / 2, y, scale(), entry.tagColor(), Bubbly.inkFor(entry.tagColor()));
				y += pillHeight + GAP;
				continue;
			}
			Bubbly.pill(context, this.textRenderer, CimText.style(entry.tag()), x, y, scale(), entry.tagColor(), Bubbly.inkFor(entry.tagColor()));
			y += pillHeight + 2;
			drawLines(context, bodies.get(i), x + 2, y, 0xFFFFFFFF);
			y += bodies.get(i).size() * lineHeight + GAP;
		}
		context.disableScissor();
		// Older lines fade out at the top instead of being sliced by the edge - and newer ones at the
		// bottom while scrolled back - matching the replies.
		if (scroll < maxScroll) {
			context.fillGradient(x - 2, top, x + width + 2, top + 12, Bubbly.PANEL_TOP, Bubbly.PANEL_TOP & 0x00FFFFFF);
		}
		if (scroll > 0) {
			int fadeColor = Bubbly.lerp(Bubbly.PANEL_TOP, Bubbly.PANEL_BOTTOM, 0.6f);
			context.fillGradient(x - 2, bottom - 12, x + width + 2, bottom, fadeColor & 0x00FFFFFF, fadeColor);
			Bubbly.sparkle(context, x + width / 2, bottom - 3, time, 0.5f);
		}
	}

	/** Draws the replies (or Close) upward from {@code bottom}; returns the top of the topmost one. */
	private int drawButtons(DrawContext context, int mouseX, int mouseY, int x, int width, int top, int bottom) {
		int lineHeight = lineHeight();
		if (mode == Mode.PICK_ITEM && playersTurn()) {
			return drawGiftPicker(context, mouseX, mouseY, x, width, bottom);
		}
		if (ended && playersTurn() && requests.isEmpty()) {
			Text label = CimText.style(Text.translatable("screen.cim.dialogue.close"));
			int height = lineHeight + 10;
			int buttonWidth = Math.min(width, readingWidth(label) + 40);
			int bx = x + (width - buttonWidth) / 2;
			int by = bottom - height;
			Hit hit = new Hit(bx, by, bx + buttonWidth, by + height, CLOSE);
			bubble(context, hit, Bubbly.TONE_BUBBLE, hit.contains(mouseX, mouseY));
			drawLines(context, wrap(label, buttonWidth), bx + (buttonWidth - readingWidth(label)) / 2, by + 5, Bubbly.INK);
			hits.add(hit);
			return by;
		}
		optionsArea = null;
		List<OptionData> options = choices();
		if (options.isEmpty()) {
			return bottom;
		}
		// Compact when crowded. The badge and the words are centered vertically in the bubble; a voice
		// reply's odds hang from the bubble's top-right edge as a little tab - the same place on every
		// bubble, taking no width from the words.
		boolean crowded = options.size() > 4;
		int pad = crowded ? 4 : 6;
		int gap = crowded ? 3 : OPTION_GAP;
		int textX = x + BADGE + 10;
		int textWidth = width - BADGE - 18;
		int pillHeight = Bubbly.pillHeight(this.textRenderer, scale());

		int count = options.size();
		List<List<OrderedText>> wrapped = new ArrayList<>();
		Text[] oddsText = new Text[count];
		int[] contentHeights = new int[count];
		int[] tabRoom = new int[count];
		int[] heights = new int[count];
		int total = 0;
		for (int i = 0; i < count; i++) {
			OptionData option = options.get(i);
			if (!option.voice().isEmpty() && option.oddsBand() >= 0) {
				oddsText[i] = CimText.style(Text.translatable("screen.cim.dialogue.odds." + option.oddsBand()));
				tabRoom[i] = pillHeight / 2;
			}
			List<OrderedText> lines = wrap(label(option), Math.max(20, textWidth));
			wrapped.add(lines);
			contentHeights[i] = Math.max(BADGE, lines.size() * lineHeight);
			heights[i] = tabRoom[i] + contentHeights[i] + pad * 2;
			total += heights[i] + (i > 0 ? gap : 0);
		}

		// Replies never take more than half the panel, so the conversation stays readable; past that
		// they scroll on their own (mouse wheel over them), and number keys still reach every one.
		int cap = Math.max(lineHeight * 3, (bottom - top) / 2);
		int areaHeight = Math.min(total, cap);
		int areaTop = bottom - areaHeight;
		optionScroll = Math.max(0, Math.min(optionScroll, total - areaHeight));
		optionsArea = new Hit(x - 2, areaTop, x + width + 2, bottom, -2);
		boolean mouseInArea = optionsArea.contains(mouseX, mouseY);

		context.enableScissor(x - 2, areaTop - 1, x + width + 2, bottom + 1);
		int y = bottom + optionScroll;
		for (int i = count - 1; i >= 0; i--) {
			OptionData option = options.get(i);
			int height = heights[i];
			y -= height;
			Hit hit = new Hit(x, Math.max(y, areaTop), x + width, Math.min(y + height, bottom), i);
			boolean visible = hit.y2() > hit.y1();
			boolean hovered = visible && mouseInArea && hit.contains(mouseX, mouseY);
			int color = option.voice().isEmpty() ? Bubbly.TONE_BUBBLE : Bubbly.lighten(Bubbly.opaque(VoiceStyle.of(option.voice()).color), 0.45f);
			int lift = hovered ? 1 : 0;
			int bubbleTop = y + tabRoom[i];
			bubble(context, new Hit(x, bubbleTop - lift, x + width, y + height - lift, i), color, hovered);

			int badgeColor = option.voice().isEmpty() ? Bubbly.ACCENT : Bubbly.darken(Bubbly.opaque(VoiceStyle.of(option.voice()).color), 0.3f);
			int contentTop = bubbleTop + pad - lift;
			int content = contentHeights[i];
			int badgeY = contentTop + (content - BADGE) / 2;
			Bubbly.panel(context, x + 5, badgeY, BADGE, BADGE, BADGE / 2, Bubbly.lighten(badgeColor, 0.2f), badgeColor);
			String number = String.valueOf(i + 1);
			context.drawText(this.textRenderer, number, x + 5 + (BADGE - this.textRenderer.getWidth(number)) / 2 + 1, badgeY + 2,
					Bubbly.inkFor(badgeColor), false);

			List<OrderedText> lines = wrapped.get(i);
			drawLines(context, lines, textX, contentTop + (content - lines.size() * lineHeight) / 2 + 1, Bubbly.INK);
			if (oddsText[i] != null) {
				int oddsWidth = Math.round(this.textRenderer.getWidth(oddsText[i]) * scale()) + 8;
				// The tab straddles the bubble's top border.
				int tabColor = Bubbly.darken(color, 0.12f);
				int tabX = x + width - oddsWidth - 10;
				int tabY = bubbleTop - pillHeight / 2 - lift;
				Bubbly.panel(context, tabX - 1, tabY - 1, oddsWidth + 2, pillHeight + 2, (pillHeight + 2) / 2,
						Bubbly.darken(color, 0.3f), Bubbly.darken(color, 0.3f));
				Bubbly.pill(context, this.textRenderer, oddsText[i], tabX, tabY, scale(), tabColor, Bubbly.INK);
			}
			if (hovered) {
				Bubbly.sparkle(context, x + 6, bubbleTop + 3 - lift, time, i);
			}
			if (visible) {
				hits.add(hit);
			}
			y -= gap;
		}
		context.disableScissor();

		// A soft fade and a sparkle at an edge that has more replies past it.
		if (optionScroll < total - areaHeight) {
			context.fillGradient(x - 2, areaTop, x + width + 2, areaTop + 10, Bubbly.PANEL_BOTTOM, Bubbly.PANEL_BOTTOM & 0x00FFFFFF);
			Bubbly.sparkle(context, x + width / 2, areaTop + 3, time, 1.5f);
		}
		if (optionScroll > 0) {
			context.fillGradient(x - 2, bottom - 10, x + width + 2, bottom, Bubbly.PANEL_BOTTOM & 0x00FFFFFF, Bubbly.PANEL_BOTTOM);
			Bubbly.sparkle(context, x + width / 2, bottom - 3, time, 3.5f);
		}
		// While the pony's replies show, a little tab above them lets you steer: bring something up instead.
		if (mode == Mode.REPLIES && !ended && !requests.isEmpty()) {
			return drawBringUpTab(context, mouseX, mouseY, x, width, areaTop);
		}
		return areaTop;
	}

	/** The "✦ Bring something up… ✦" tab, centered just above {@code above}; returns its top. */
	private int drawBringUpTab(DrawContext context, int mouseX, int mouseY, int x, int width, int above) {
		Text label = CimText.style(Text.translatable("screen.cim.dialogue.bring_up"));
		int pillHeight = Bubbly.pillHeight(this.textRenderer, scale());
		int tabWidth = Math.round(this.textRenderer.getWidth(label) * scale()) + 12;
		int tabX = x + (width - tabWidth) / 2;
		int tabY = above - OPTION_GAP - pillHeight;
		Hit hit = new Hit(tabX, tabY, tabX + tabWidth, tabY + pillHeight, BRING_UP);
		boolean hovered = hit.contains(mouseX, mouseY);
		Bubbly.pill(context, this.textRenderer, label, tabX, tabY, scale(), hovered ? Bubbly.lighten(Bubbly.ACCENT, 0.3f) : Bubbly.ACCENT,
				Bubbly.inkFor(Bubbly.ACCENT));
		if (hovered) {
			Bubbly.sparkle(context, tabX - 4, tabY + pillHeight / 2, time, 0.7f);
			Bubbly.sparkle(context, tabX + tabWidth + 3, tabY + pillHeight / 2, time, 2.7f);
		}
		hits.add(hit);
		return tabY;
	}

	/**
	 * Picking a gift: "What will you give?", then a cell per kind of item in your inventory (number
	 * keys pick the first nine), and "Never mind" below. Hovering a cell names the item.
	 */
	private int drawGiftPicker(DrawContext context, int mouseX, int mouseY, int x, int width, int bottom) {
		int lineHeight = lineHeight();
		List<Integer> slots = giftSlots();
		// "Never mind" at the very bottom.
		Text back = CimText.style(Text.translatable("screen.cim.dialogue.never_mind"));
		int backHeight = lineHeight + 10;
		int backWidth = Math.min(width, readingWidth(back) + 40);
		int backX = x + (width - backWidth) / 2;
		int backY = bottom - backHeight;
		Hit backHit = new Hit(backX, backY, backX + backWidth, backY + backHeight, BRING_UP);
		bubble(context, backHit, Bubbly.TONE_BUBBLE, backHit.contains(mouseX, mouseY));
		drawLines(context, wrap(back, backWidth), backX + (backWidth - readingWidth(back)) / 2, backY + 5, Bubbly.INK);
		hits.add(backHit);

		int perRow = Math.max(1, width / ITEM_CELL);
		int rows = Math.max(1, (slots.size() + perRow - 1) / perRow);
		int gridBottom = backY - GAP;
		int gridTop = gridBottom - rows * ITEM_CELL;
		if (slots.isEmpty()) {
			Text none = CimText.style(Text.translatable("screen.cim.dialogue.nothing_to_give"));
			drawLines(context, wrap(none, width), x, gridBottom - lineHeight, 0xFFC9B8E0);
			gridTop = gridBottom - lineHeight;
		}
		Text hoveredName = null;
		for (int i = 0; i < slots.size(); i++) {
			int cellX = x + (i % perRow) * ITEM_CELL;
			int cellY = gridTop + (i / perRow) * ITEM_CELL;
			int slot = slots.get(i);
			Hit hit = new Hit(cellX, cellY, cellX + ITEM_CELL - 2, cellY + ITEM_CELL - 2, ITEM - slot);
			boolean hovered = hit.contains(mouseX, mouseY);
			bubble(context, hit, Bubbly.TONE_BUBBLE, hovered);
			net.minecraft.item.ItemStack stack = this.client.player.getInventory().main.get(slot);
			context.drawItem(stack, cellX + 2, cellY + 2);
			if (hovered) {
				hoveredName = stack.getName();
			}
			hits.add(hit);
		}
		Text title = CimText.style(Text.translatable("screen.cim.dialogue.pick_gift"));
		int titleY = gridTop - GAP - lineHeight;
		drawLines(context, wrap(title, width), x, titleY, 0xFFFFFFFF);
		if (hoveredName != null) {
			context.drawTooltip(this.textRenderer, hoveredName, mouseX, mouseY);
		}
		return titleY;
	}

	private void bubble(DrawContext context, Hit hit, int color, boolean hovered) {
		int fill = hovered ? Bubbly.lighten(color, 0.35f) : color;
		int border = hovered ? 0xFFFFFFFF : Bubbly.darken(color, 0.25f);
		Bubbly.framed(context, hit.x1(), hit.y1(), hit.x2() - hit.x1(), hit.y2() - hit.y1(), 7, border,
				Bubbly.lighten(fill, 0.25f), fill);
	}

	/**
	 * A tone reply shows its words, or its tone ("Friendly") when it has none; a voice reply shows its
	 * voice's symbol before its words. Odds are drawn separately, as a pill - never a number.
	 */
	private static Text label(OptionData option) {
		if (!option.voice().isEmpty()) {
			VoiceStyle style = VoiceStyle.of(option.voice());
			return Text.literal(style.symbol + " ").styled(s -> s.withColor(Bubbly.darken(Bubbly.opaque(style.color), 0.45f) & 0xFFFFFF))
					.append(Text.literal(option.text()));
		}
		if (!option.text().isEmpty()) {
			return Text.literal(option.text());
		}
		return option.tone().isEmpty() ? Text.literal("...") : Text.translatable("screen.cim.idle_chatter." + option.tone());
	}

	/** The first {@code chars} characters of {@code text}, keeping its colors and styles. */
	private static Text truncate(Text text, int chars) {
		MutableText out = Text.empty();
		int[] left = {chars};
		text.visit((style, string) -> {
			if (left[0] <= 0) {
				return StringVisitable.TERMINATE_VISIT;
			}
			String part = string.length() <= left[0] ? string : string.substring(0, left[0]);
			out.append(Text.literal(part).setStyle(style));
			left[0] -= part.length();
			return left[0] <= 0 ? StringVisitable.TERMINATE_VISIT : Optional.<Unit>empty();
		}, Style.EMPTY);
		return out;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		ClientPlayNetworking.send(new StopVillagerInteractionC2SPayload(entityId));
		super.close();
	}
}
