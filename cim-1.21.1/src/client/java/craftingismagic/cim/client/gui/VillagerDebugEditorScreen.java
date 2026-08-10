package craftingismagic.cim.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import craftingismagic.cim.network.DebugSetVillagerAffectionC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerFavoriteGiftC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerGeneticsC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerLifeStageC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerNameC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerPersonalityC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerRelationshipTypeC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerSpeciesC2SPayload;
import craftingismagic.cim.network.DebugToggleChangelingDisguiseC2SPayload;
import craftingismagic.cim.network.OpenVillagerDebugEditorS2CPayload;
import craftingismagic.cim.network.StopVillagerInteractionC2SPayload;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.villager.FavoriteGiftAttachment;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;
import craftingismagic.cim.villager.PersonalityType;
import craftingismagic.cim.villager.PonyBodyType;
import craftingismagic.cim.villager.PonyGeneticsAttachment.ColorGenes;
import craftingismagic.cim.villager.PonyGeneticsAttachment.EyeGenes;
import craftingismagic.cim.villager.PonyGeneticsAttachment.ManeGenes;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;
import craftingismagic.cim.villager.PonyTailLength;
import craftingismagic.cim.villager.PonyTailShape;
import craftingismagic.cim.villager.Quirk;
import craftingismagic.cim.villager.RelationshipType;

/**
 * Creative-only villager debug editor (opened by right-clicking a villager while holding {@code
 * cim:villager_debug_wand}) - full control over name, species, life stage, genetics, personality,
 * and this player's relationship with the villager, tabbed like {@link JournalScreen} to keep any
 * one tab's widget count manageable. Every widget applies live (a network packet per change) rather
 * than needing a separate Save button - see {@code VillagerDebugEditorHandler} on the server, which
 * independently re-checks Creative mode rather than trusting this screen's own gating.
 * <p>
 * {@link #state} is a plain mutable holder (not a record) carried by reference across tab
 * switches - each tab switch constructs a fresh screen instance (same pattern as {@code
 * JournalScreen}), so edits made on one tab need to survive navigating to another before this
 * screen closes.
 */
public class VillagerDebugEditorScreen extends Screen {
	private static final int CONTENT_WIDTH = 320;
	private static final int TITLE_Y = 12;
	private static final int TABS_Y = 28;
	private static final int TAB_BUTTON_WIDTH = 74;
	private static final int TAB_BUTTON_HEIGHT = 20;
	private static final int TAB_SPACING = 4;
	private static final int BODY_Y = TABS_Y + TAB_BUTTON_HEIGHT + 10;
	private static final int ROW_HEIGHT = 20;
	private static final int FIELD_HEIGHT = 16;
	private static final int COLUMN_GAP = 10;
	private static final int LABEL_WIDTH = 70;

	public enum Tab {
		IDENTITY("screen.cim.villager_debug_editor.tab_identity"),
		GENETICS("screen.cim.villager_debug_editor.tab_genetics"),
		PERSONALITY("screen.cim.villager_debug_editor.tab_personality"),
		RELATIONSHIP("screen.cim.villager_debug_editor.tab_relationship"),
		FAMILY("screen.cim.villager_debug_editor.tab_family");

		private final String translationKey;

		Tab(String translationKey) {
			this.translationKey = translationKey;
		}

		Text label() {
			return Text.translatable(translationKey);
		}
	}

	private final int villagerEntityId;
	private final EditorState state;
	private final Tab activeTab;
	private final List<LabeledField> labeledFields = new ArrayList<>();

	public static VillagerDebugEditorScreen fromPayload(OpenVillagerDebugEditorS2CPayload payload) {
		EditorState state = new EditorState();
		state.name = payload.identity().name();
		state.species = payload.identity().species();
		state.lifeStage = payload.identity().lifeStage();
		state.growthTicks = payload.identity().growthTicks();
		state.changelingInfo = payload.identity().changeling();
		state.genetics = payload.genetics();
		state.personality = payload.personality();
		state.affection = payload.relationship().affection();
		state.relationshipType = payload.relationship().relationshipType();
		state.favoriteGift = payload.relationship().favoriteGift();
		state.friendGroup = payload.relationship().friendGroupNames();
		state.family = payload.family();
		return new VillagerDebugEditorScreen(payload.villagerEntityId(), state, Tab.IDENTITY);
	}

	private VillagerDebugEditorScreen(int villagerEntityId, EditorState state, Tab activeTab) {
		super(Text.translatable("screen.cim.villager_debug_editor.title"));
		this.villagerEntityId = villagerEntityId;
		this.state = state;
		this.activeTab = activeTab;
	}

	@Override
	protected void init() {
		labeledFields.clear();

		int tabsWidth = Tab.values().length * TAB_BUTTON_WIDTH + (Tab.values().length - 1) * TAB_SPACING;
		int tabX = (this.width - tabsWidth) / 2;
		for (Tab tab : Tab.values()) {
			ButtonWidget button = ButtonWidget.builder(tab.label(), b -> this.client.setScreen(new VillagerDebugEditorScreen(villagerEntityId, state, tab)))
					.dimensions(tabX, TABS_Y, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT)
					.build();
			button.active = tab != activeTab;
			this.addDrawableChild(button);
			tabX += TAB_BUTTON_WIDTH + TAB_SPACING;
		}

		switch (activeTab) {
			case IDENTITY -> buildIdentityTab();
			case GENETICS -> buildGeneticsTab();
			case PERSONALITY -> buildPersonalityTab();
			case RELATIONSHIP -> buildRelationshipTab();
			case FAMILY -> buildFamilyTab();
		}

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.cim.villager_debug_editor.close"), b -> this.close())
				.dimensions((this.width - TAB_BUTTON_WIDTH) / 2, this.height - 28, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT)
				.build());
	}

	// ---- Identity tab ----

	private void buildIdentityTab() {
		int centerX = this.width / 2;
		int x = centerX - CONTENT_WIDTH / 2;
		int y = BODY_Y;

		TextFieldWidget nameField = new TextFieldWidget(this.textRenderer, x + LABEL_WIDTH, y, CONTENT_WIDTH - LABEL_WIDTH, FIELD_HEIGHT, Text.empty());
		nameField.setMaxLength(64);
		nameField.setText(state.name);
		nameField.setChangedListener(value -> {
			state.name = value;
			ClientPlayNetworking.send(new DebugSetVillagerNameC2SPayload(villagerEntityId, value));
		});
		this.addDrawableChild(nameField);
		labeledFields.add(new LabeledField("screen.cim.villager_debug_editor.name", y));
		y += ROW_HEIGHT;

		this.addDrawableChild(CyclingButtonWidget.builder((Species s) -> Text.translatable(s.translationKey()))
				.values(Species.values())
				.initially(state.species)
				.build(x, y, CONTENT_WIDTH, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.species"), (b, value) -> {
					state.species = value;
					ClientPlayNetworking.send(new DebugSetVillagerSpeciesC2SPayload(villagerEntityId, value));
				}));
		y += ROW_HEIGHT;

		this.addDrawableChild(CyclingButtonWidget.builder((LifeStage stage) -> Text.literal(titleCase(stage.name())))
				.values(LifeStage.values())
				.initially(state.lifeStage)
				.build(x, y, CONTENT_WIDTH, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.life_stage"), (b, value) -> {
					state.lifeStage = value;
					ClientPlayNetworking.send(new DebugSetVillagerLifeStageC2SPayload(villagerEntityId, value, state.growthTicks));
				}));
		y += ROW_HEIGHT;

		TextFieldWidget growthField = new TextFieldWidget(this.textRenderer, x + LABEL_WIDTH, y, CONTENT_WIDTH - LABEL_WIDTH, FIELD_HEIGHT, Text.empty());
		growthField.setTextPredicate(value -> value.isEmpty() || value.matches("-?[0-9]{0,9}"));
		growthField.setText(Integer.toString(state.growthTicks));
		growthField.setChangedListener(value -> {
			int parsed = parseIntOrKeep(value, state.growthTicks);
			state.growthTicks = parsed;
			ClientPlayNetworking.send(new DebugSetVillagerLifeStageC2SPayload(villagerEntityId, state.lifeStage, parsed));
		});
		this.addDrawableChild(growthField);
		labeledFields.add(new LabeledField("screen.cim.villager_debug_editor.growth_ticks", y));
		y += ROW_HEIGHT;

		// Only a real Changeling ever has a non-empty trueName - everyone else (including a
		// Changeling that somehow hasn't been assigned a disguise yet) gets ChangelingInfo.NONE.
		if (!state.changelingInfo.trueName().isEmpty()) {
			ButtonWidget disguiseButton = ButtonWidget.builder(disguiseButtonLabel(state.changelingInfo.disguised()), b -> {
				boolean nowDisguised = !state.changelingInfo.disguised();
				state.changelingInfo = new OpenVillagerDebugEditorS2CPayload.ChangelingInfo(
						nowDisguised, state.changelingInfo.trueName(), state.changelingInfo.disguiseName());
				state.name = nowDisguised ? state.changelingInfo.disguiseName() : state.changelingInfo.trueName();
				nameField.setText(state.name);
				b.setMessage(disguiseButtonLabel(nowDisguised));
				ClientPlayNetworking.send(new DebugToggleChangelingDisguiseC2SPayload(villagerEntityId));
			}).dimensions(x, y, CONTENT_WIDTH, FIELD_HEIGHT).build();
			this.addDrawableChild(disguiseButton);
		}
	}

	private static Text disguiseButtonLabel(boolean disguised) {
		return Text.translatable(disguised
				? "screen.cim.villager_debug_editor.dispel_disguise"
				: "screen.cim.villager_debug_editor.return_disguise");
	}

	// ---- Genetics tab (two columns to keep the tab a manageable height) ----

	private void buildGeneticsTab() {
		int centerX = this.width / 2;
		int leftX = centerX - CONTENT_WIDTH / 2;
		int columnWidth = (CONTENT_WIDTH - COLUMN_GAP) / 2;
		int rightX = leftX + columnWidth + COLUMN_GAP;

		int leftY = BODY_Y;
		ColorGenes coat = state.genetics.coat();
		leftY = addSlider(leftX, leftY, columnWidth, "screen.cim.villager_debug_editor.coat_hue", coat.hue(),
				v -> updateGenetics(g -> withCoat(g, c -> new ColorGenes((float) v, c.vividness(), c.whiteCoat(), c.darkCoat()))));
		leftY = addSlider(leftX, leftY, columnWidth, "screen.cim.villager_debug_editor.coat_vividness", coat.vividness(),
				v -> updateGenetics(g -> withCoat(g, c -> new ColorGenes(c.hue(), (float) v, c.whiteCoat(), c.darkCoat()))));
		leftY = addSlider(leftX, leftY, columnWidth, "screen.cim.villager_debug_editor.coat_white", coat.whiteCoat(),
				v -> updateGenetics(g -> withCoat(g, c -> new ColorGenes(c.hue(), c.vividness(), (float) v, c.darkCoat()))));
		leftY = addSlider(leftX, leftY, columnWidth, "screen.cim.villager_debug_editor.coat_dark", coat.darkCoat(),
				v -> updateGenetics(g -> withCoat(g, c -> new ColorGenes(c.hue(), c.vividness(), c.whiteCoat(), (float) v))));

		ManeGenes mane = state.genetics.mane();
		this.addDrawableChild(CyclingButtonWidget.builder((Integer count) -> Text.literal(count.toString()))
				.values(1, 2)
				.initially(mane.hueCount())
				.build(leftX, leftY, columnWidth, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.mane_hue_count"), (b, value) ->
						updateGenetics(g -> withMane(g, m -> new ManeGenes(value, m.huePrimary(), m.hueSecondary(), m.vividness())))));
		leftY += ROW_HEIGHT;
		leftY = addSlider(leftX, leftY, columnWidth, "screen.cim.villager_debug_editor.mane_hue", mane.huePrimary(),
				v -> updateGenetics(g -> withMane(g, m -> new ManeGenes(m.hueCount(), (float) v, m.hueSecondary(), m.vividness()))));
		leftY = addSlider(leftX, leftY, columnWidth, "screen.cim.villager_debug_editor.mane_hue2", mane.hueSecondary(),
				v -> updateGenetics(g -> withMane(g, m -> new ManeGenes(m.hueCount(), m.huePrimary(), (float) v, m.vividness()))));
		addSlider(leftX, leftY, columnWidth, "screen.cim.villager_debug_editor.mane_vividness", mane.vividness(),
				v -> updateGenetics(g -> withMane(g, m -> new ManeGenes(m.hueCount(), m.huePrimary(), m.hueSecondary(), (float) v))));

		int rightY = BODY_Y;
		EyeGenes eye = state.genetics.eye();
		this.addDrawableChild(CyclingButtonWidget.builder((Integer count) -> Text.literal(count.toString()))
				.values(1, 2)
				.initially(eye.hueCount())
				.build(rightX, rightY, columnWidth, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.eye_hue_count"), (b, value) ->
						updateGenetics(g -> withEye(g, e -> new EyeGenes(value, e.huePrimary(), e.hueSecondary(), e.vividness())))));
		rightY += ROW_HEIGHT;
		rightY = addSlider(rightX, rightY, columnWidth, "screen.cim.villager_debug_editor.eye_hue", eye.huePrimary(),
				v -> updateGenetics(g -> withEye(g, e -> new EyeGenes(e.hueCount(), (float) v, e.hueSecondary(), e.vividness()))));
		rightY = addSlider(rightX, rightY, columnWidth, "screen.cim.villager_debug_editor.eye_hue2", eye.hueSecondary(),
				v -> updateGenetics(g -> withEye(g, e -> new EyeGenes(e.hueCount(), e.huePrimary(), (float) v, e.vividness()))));
		rightY = addSlider(rightX, rightY, columnWidth, "screen.cim.villager_debug_editor.eye_vividness", eye.vividness(),
				v -> updateGenetics(g -> withEye(g, e -> new EyeGenes(e.hueCount(), e.huePrimary(), e.hueSecondary(), (float) v))));

		this.addDrawableChild(CyclingButtonWidget.builder((PonyTailLength tl) -> Text.literal(titleCase(tl.name())))
				.values(PonyTailLength.values())
				.initially(state.genetics.tailLength())
				.build(rightX, rightY, columnWidth, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.tail_length"), (b, value) ->
						updateGenetics(g -> new PonyGenetics(g.coat(), g.mane(), g.eye(), value, g.tailShape(), g.bodyType()))));
		rightY += ROW_HEIGHT;

		this.addDrawableChild(CyclingButtonWidget.builder((PonyTailShape ts) -> Text.literal(titleCase(ts.name())))
				.values(PonyTailShape.values())
				.initially(state.genetics.tailShape())
				.build(rightX, rightY, columnWidth, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.tail_shape"), (b, value) ->
						updateGenetics(g -> new PonyGenetics(g.coat(), g.mane(), g.eye(), g.tailLength(), value, g.bodyType()))));
		rightY += ROW_HEIGHT;

		this.addDrawableChild(CyclingButtonWidget.builder((PonyBodyType bt) -> Text.literal(titleCase(bt.name())))
				.values(PonyBodyType.values())
				.initially(state.genetics.bodyType())
				.build(rightX, rightY, columnWidth, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.body_type"), (b, value) ->
						updateGenetics(g -> new PonyGenetics(g.coat(), g.mane(), g.eye(), g.tailLength(), g.tailShape(), value))));
	}

	private static PonyGenetics withCoat(PonyGenetics genetics, java.util.function.Function<ColorGenes, ColorGenes> mutator) {
		return new PonyGenetics(mutator.apply(genetics.coat()), genetics.mane(), genetics.eye(), genetics.tailLength(), genetics.tailShape(), genetics.bodyType());
	}

	private static PonyGenetics withMane(PonyGenetics genetics, java.util.function.Function<ManeGenes, ManeGenes> mutator) {
		return new PonyGenetics(genetics.coat(), mutator.apply(genetics.mane()), genetics.eye(), genetics.tailLength(), genetics.tailShape(), genetics.bodyType());
	}

	private static PonyGenetics withEye(PonyGenetics genetics, java.util.function.Function<EyeGenes, EyeGenes> mutator) {
		return new PonyGenetics(genetics.coat(), genetics.mane(), mutator.apply(genetics.eye()), genetics.tailLength(), genetics.tailShape(), genetics.bodyType());
	}

	private void updateGenetics(java.util.function.Function<PonyGenetics, PonyGenetics> mutator) {
		state.genetics = mutator.apply(state.genetics);
		ClientPlayNetworking.send(new DebugSetVillagerGeneticsC2SPayload(villagerEntityId, state.genetics));
	}

	// ---- Personality tab ----

	private void buildPersonalityTab() {
		int centerX = this.width / 2;
		int x = centerX - CONTENT_WIDTH / 2;
		int y = BODY_Y;

		this.addDrawableChild(CyclingButtonWidget.builder((PersonalityType t) -> Text.literal(titleCase(t.name())))
				.values(PersonalityType.values())
				.initially(state.personality.baseType())
				.build(x, y, CONTENT_WIDTH, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.base_type"), (b, value) ->
						updatePersonality(p -> new Personality(value, p.quirks()))));
		y += ROW_HEIGHT;

		int columnWidth = (CONTENT_WIDTH - COLUMN_GAP) / 2;
		int rightX = x + columnWidth + COLUMN_GAP;
		Quirk[] quirks = Quirk.values();
		int leftY = y;
		int rightY = y;
		for (int i = 0; i < quirks.length; i++) {
			Quirk quirk = quirks[i];
			boolean left = i % 2 == 0;
			int quirkX = left ? x : rightX;
			int quirkY = left ? leftY : rightY;
			this.addDrawableChild(CyclingButtonWidget.onOffBuilder(state.personality.quirks().contains(quirk))
					.build(quirkX, quirkY, columnWidth, FIELD_HEIGHT, Text.literal(titleCase(quirk.name())), (b, enabled) ->
							updatePersonality(p -> {
								List<Quirk> updated = new ArrayList<>(p.quirks());
								if (enabled) {
									if (!updated.contains(quirk)) {
										updated.add(quirk);
									}
								} else {
									updated.remove(quirk);
								}
								return new Personality(p.baseType(), updated);
							})));
			if (left) {
				leftY += ROW_HEIGHT;
			} else {
				rightY += ROW_HEIGHT;
			}
		}
	}

	private void updatePersonality(java.util.function.Function<Personality, Personality> mutator) {
		state.personality = mutator.apply(state.personality);
		ClientPlayNetworking.send(new DebugSetVillagerPersonalityC2SPayload(villagerEntityId, state.personality));
	}

	// ---- Relationship tab ----

	private void buildRelationshipTab() {
		int centerX = this.width / 2;
		int x = centerX - CONTENT_WIDTH / 2;
		int y = BODY_Y;

		TextFieldWidget affectionField = new TextFieldWidget(this.textRenderer, x + LABEL_WIDTH, y, CONTENT_WIDTH - LABEL_WIDTH, FIELD_HEIGHT, Text.empty());
		affectionField.setTextPredicate(value -> value.isEmpty() || value.equals("-") || value.matches("-?[0-9]{0,9}"));
		affectionField.setText(Integer.toString(state.affection));
		affectionField.setChangedListener(value -> {
			int parsed = parseIntOrKeep(value, state.affection);
			state.affection = parsed;
			ClientPlayNetworking.send(new DebugSetVillagerAffectionC2SPayload(villagerEntityId, parsed));
		});
		this.addDrawableChild(affectionField);
		labeledFields.add(new LabeledField("screen.cim.villager_debug_editor.affection", y));
		y += ROW_HEIGHT;

		this.addDrawableChild(CyclingButtonWidget.builder((RelationshipType t) -> Text.literal(titleCase(t.name())))
				.values(RelationshipType.values())
				.initially(state.relationshipType)
				.build(x, y, CONTENT_WIDTH, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.relationship_type"), (b, value) -> {
					state.relationshipType = value;
					ClientPlayNetworking.send(new DebugSetVillagerRelationshipTypeC2SPayload(villagerEntityId, value));
				}));
		y += ROW_HEIGHT;

		List<Item> giftPool = FavoriteGiftAttachment.giftPool();
		this.addDrawableChild(CyclingButtonWidget.builder((Item item) -> item.getName())
				.values(giftPool)
				.initially(state.favoriteGift)
				.build(x, y, CONTENT_WIDTH, FIELD_HEIGHT, Text.translatable("screen.cim.villager_debug_editor.favorite_gift"), (b, value) -> {
					state.favoriteGift = value;
					ClientPlayNetworking.send(new DebugSetVillagerFavoriteGiftC2SPayload(villagerEntityId, value));
				}));
		y += ROW_HEIGHT + 6;

		buildFriendGroupChart(centerX, y);
	}

	/** Read-only, same reasoning as the Family tab's chart (see {@link #buildFamilyTab}) - editing
	 * group membership would need a villager-picker UI, a bigger lift deferred for now. Structurally
	 * the same single-elbow shape Family already uses for self-to-children (a flat clique has no
	 * parent/partner roles to lay out, just self plus a row of peers), so it reuses that
	 * infrastructure directly rather than inventing new geometry. */
	private void buildFriendGroupChart(int centerX, int y) {
		friendGroupNodes.clear();
		friendGroupLines.clear();

		int selfWidth = nodeWidth(state.name);
		TreeNode selfNode = new TreeNode(centerX - selfWidth / 2, y, selfWidth, TREE_NODE_HEIGHT, state.name, NodeRole.SELF);
		friendGroupNodes.add(selfNode);

		List<String> friendLabels = state.friendGroup.isEmpty()
				? List.of(placeholder("friend_group_none"))
				: state.friendGroup;
		int rowY = y + TREE_NODE_HEIGHT + TREE_ROW_GAP;
		List<TreeNode> friendNodes = layoutRow(friendLabels, centerX, rowY);
		friendGroupNodes.addAll(friendNodes);

		addElbowConnector(selfNode.centerX(), y + TREE_NODE_HEIGHT, friendNodes, rowY, friendGroupLines);
	}

	// ---- Shared genealogy/relationship-chart infrastructure, used by both the Family tab (an
	// actual genealogy-chart layout - read-only, see OpenVillagerDebugEditorS2CPayload.Family's own
	// doc comment for why this views but doesn't edit family links) and the Relationship tab's
	// friend-group chart (see buildFriendGroupChart). Nodes/lines are computed once per tab build
	// and drawn manually in render() (a Screen widget can't draw arbitrary connector lines between
	// other widgets, so this bypasses the widget system entirely for these two tabs). ----

	private static final int TREE_NODE_HEIGHT = 20;
	private static final int TREE_NODE_MIN_WIDTH = 70;
	private static final int TREE_NODE_PADDING = 8;
	private static final int TREE_NODE_GAP = 8;
	private static final int TREE_ROW_GAP = 36;
	private static final int TREE_SELF_COLOR = 0xFFB8860B;
	private static final int TREE_PARTNER_COLOR = 0xFF8B4A62;
	private static final int TREE_NODE_COLOR = 0xFF3B4252;
	private static final int TREE_LINE_COLOR = 0xFFAAAAAA;
	private static final int TREE_LINE_THICKNESS = 2;

	private final List<TreeNode> familyTreeNodes = new ArrayList<>();
	private final List<TreeLine> familyTreeLines = new ArrayList<>();
	private final List<TreeNode> friendGroupNodes = new ArrayList<>();
	private final List<TreeLine> friendGroupLines = new ArrayList<>();

	private enum NodeRole { NORMAL, SELF, PARTNER }

	private record TreeNode(int x, int y, int width, int height, String label, NodeRole role) {
		int centerX() {
			return x + width / 2;
		}
	}

	private record TreeLine(int x1, int y1, int x2, int y2) {
	}

	private void buildFamilyTab() {
		familyTreeNodes.clear();
		familyTreeLines.clear();
		OpenVillagerDebugEditorS2CPayload.Family family = state.family;
		int centerX = this.width / 2;
		int y = BODY_Y + 6;

		// Parents row - always 2 slots (padded with a placeholder) so the tree shape stays
		// consistent regardless of how much family data is actually known, except an orphan gets a
		// single wide label instead and no connector down to the row below.
		List<String> parentLabels = family.orphan()
				? List.of(placeholder("family_orphan"))
				: padTo(family.parents(), 2, placeholder("family_none"));
		List<TreeNode> parentNodes = layoutRow(parentLabels, centerX, y);
		familyTreeNodes.addAll(parentNodes);
		int parentBottomY = y + TREE_NODE_HEIGHT;
		y += TREE_NODE_HEIGHT + TREE_ROW_GAP;

		// Middle row - self is always centered exactly on centerX (and therefore always dead in line
		// with the parents' own connector below), by construction rather than by list-index math:
		// siblings stack outward to self's left, partner sits immediately to self's right. An
		// earlier version instead inserted self into a shared, jointly-centered labels list (self at
		// index siblings.size()/2) - that put self off-center the moment a partner shifted the
		// row's total width, which visually disconnected the parent connector from self's box
		// (the spine only spanned the blood-relative nodes' own centers, never reaching back to the
		// parents' actual stub position). Anchoring self first avoids that whole bug class.
		int selfWidth = nodeWidth(state.name);
		TreeNode selfNode = new TreeNode(centerX - selfWidth / 2, y, selfWidth, TREE_NODE_HEIGHT, state.name, NodeRole.SELF);
		familyTreeNodes.add(selfNode);

		List<TreeNode> siblingNodes = new ArrayList<>();
		int leftEdge = selfNode.x();
		for (String sibling : family.siblings()) {
			int width = nodeWidth(sibling);
			leftEdge -= width + TREE_NODE_GAP;
			siblingNodes.add(new TreeNode(leftEdge, y, width, TREE_NODE_HEIGHT, sibling, NodeRole.NORMAL));
		}
		Collections.reverse(siblingNodes);
		familyTreeNodes.addAll(siblingNodes);

		TreeNode partnerNode = null;
		if (!family.partner().isEmpty()) {
			int width = nodeWidth(family.partner());
			int px = selfNode.x() + selfNode.width() + TREE_NODE_GAP;
			partnerNode = new TreeNode(px, y, width, TREE_NODE_HEIGHT, family.partner(), NodeRole.PARTNER);
			familyTreeNodes.add(partnerNode);
		}
		int middleBottomY = y + TREE_NODE_HEIGHT;
		y += TREE_NODE_HEIGHT + TREE_ROW_GAP;

		// Children row - only the blood-relative nodes above (self + siblings, never partner) hang
		// off the parents' connector; only self hangs a connector down to the children row.
		List<String> childLabels = padTo(family.children(), 1, placeholder("family_none"));
		List<TreeNode> childNodes = layoutRow(childLabels, centerX, y);
		familyTreeNodes.addAll(childNodes);

		List<TreeNode> bloodRelatives = new ArrayList<>(siblingNodes);
		bloodRelatives.add(selfNode);
		if (!family.orphan()) {
			addElbowConnector(parentsMidX(parentNodes, parentBottomY), parentBottomY, bloodRelatives, selfNode.y(), familyTreeLines);
		}
		if (partnerNode != null) {
			int lineY = selfNode.y() + TREE_NODE_HEIGHT / 2;
			familyTreeLines.add(new TreeLine(selfNode.x() + selfNode.width(), lineY, partnerNode.x(), lineY));
		}
		addElbowConnector(selfNode.centerX(), middleBottomY, childNodes, childNodes.get(0).y(), familyTreeLines);
	}

	private int nodeWidth(String label) {
		return Math.max(TREE_NODE_MIN_WIDTH, this.textRenderer.getWidth(label) + TREE_NODE_PADDING * 2);
	}

	private int parentsMidX(List<TreeNode> parentNodes, int parentBottomY) {
		if (parentNodes.size() == 2) {
			int lineY = parentNodes.get(0).y() + TREE_NODE_HEIGHT / 2;
			familyTreeLines.add(new TreeLine(parentNodes.get(0).x() + parentNodes.get(0).width(), lineY, parentNodes.get(1).x(), lineY));
			return (parentNodes.get(0).centerX() + parentNodes.get(1).centerX()) / 2;
		}
		return parentNodes.get(0).centerX();
	}

	/**
	 * The standard genealogy-chart elbow: a vertical stub from ({@code fromX}, {@code fromY}) down
	 * to a horizontal spine, then a vertical drop from the spine to each of {@code targets}, written
	 * into {@code lines} (the caller's own node/line lists - Family and the Relationship tab's
	 * friend-group chart each keep separate lists so both tabs' charts can coexist across a tab
	 * switch). The spine's own span is seeded with {@code fromX} itself (not just the targets'
	 * centers) - the bug fixed earlier this feature's history was exactly a spine that stopped short
	 * of the stub above it because it only spanned the target nodes, which don't necessarily
	 * straddle {@code fromX}.
	 */
	private void addElbowConnector(int fromX, int fromY, List<TreeNode> targets, int topY, List<TreeLine> lines) {
		int spineY = fromY + TREE_ROW_GAP / 2;
		lines.add(new TreeLine(fromX, fromY, fromX, spineY));

		int leftX = fromX;
		int rightX = fromX;
		for (TreeNode node : targets) {
			leftX = Math.min(leftX, node.centerX());
			rightX = Math.max(rightX, node.centerX());
			lines.add(new TreeLine(node.centerX(), spineY, node.centerX(), topY));
		}
		lines.add(new TreeLine(leftX, spineY, rightX, spineY));
	}

	/** For the parents/children rows only - the middle row (self/siblings/partner) is laid out
	 * separately in {@link #buildFamilyTab} instead, anchored on self rather than jointly centered. */
	private List<TreeNode> layoutRow(List<String> labels, int centerX, int y) {
		List<Integer> widths = new ArrayList<>();
		int totalWidth = -TREE_NODE_GAP;
		for (String label : labels) {
			int width = nodeWidth(label);
			widths.add(width);
			totalWidth += width + TREE_NODE_GAP;
		}

		List<TreeNode> nodes = new ArrayList<>();
		int x = centerX - totalWidth / 2;
		for (int i = 0; i < labels.size(); i++) {
			int width = widths.get(i);
			nodes.add(new TreeNode(x, y, width, TREE_NODE_HEIGHT, labels.get(i), NodeRole.NORMAL));
			x += width + TREE_NODE_GAP;
		}
		return nodes;
	}

	private static List<String> padTo(List<String> names, int minSize, String placeholder) {
		List<String> padded = new ArrayList<>(names);
		while (padded.size() < minSize) {
			padded.add(placeholder);
		}
		return padded;
	}

	private static String placeholder(String key) {
		return Text.translatable("screen.cim.villager_debug_editor." + key).getString();
	}

	private void renderTree(DrawContext context, List<TreeNode> nodes, List<TreeLine> lines) {
		for (TreeLine line : lines) {
			if (line.y1() == line.y2()) {
				context.fill(line.x1(), line.y1() - TREE_LINE_THICKNESS / 2, line.x2(), line.y1() + TREE_LINE_THICKNESS / 2, TREE_LINE_COLOR);
			} else {
				context.fill(line.x1() - TREE_LINE_THICKNESS / 2, line.y1(), line.x1() + TREE_LINE_THICKNESS / 2, line.y2(), TREE_LINE_COLOR);
			}
		}
		for (TreeNode node : nodes) {
			int color = switch (node.role()) {
				case SELF -> TREE_SELF_COLOR;
				case PARTNER -> TREE_PARTNER_COLOR;
				case NORMAL -> TREE_NODE_COLOR;
			};
			context.fill(node.x(), node.y(), node.x() + node.width(), node.y() + node.height(), color);
			context.drawCenteredTextWithShadow(this.textRenderer, node.label(), node.centerX(), node.y() + (node.height() - 8) / 2, 0xFFFFFF);
		}
	}

	// ---- Shared helpers ----

	private int addSlider(int x, int y, int width, String translationKey, float initial, DoubleConsumer onChange) {
		this.addDrawableChild(new LabeledSliderWidget(x, y, width, FIELD_HEIGHT, Text.translatable(translationKey), initial, onChange));
		return y + ROW_HEIGHT;
	}

	private static int parseIntOrKeep(String value, int fallback) {
		try {
			return value.isEmpty() || value.equals("-") ? fallback : Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String titleCase(String enumName) {
		String[] words = enumName.split("_");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(word.substring(0, 1)).append(word.substring(1).toLowerCase(Locale.ROOT));
		}
		return result.toString();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, TITLE_Y, 0xFFFFFF);

		int x = this.width / 2 - CONTENT_WIDTH / 2;
		for (LabeledField field : labeledFields) {
			context.drawTextWithShadow(this.textRenderer, Text.translatable(field.translationKey()), x, field.y() + 4, 0xAAAAAA);
		}

		if (activeTab == Tab.FAMILY) {
			renderTree(context, familyTreeNodes, familyTreeLines);
		} else if (activeTab == Tab.RELATIONSHIP) {
			renderTree(context, friendGroupNodes, friendGroupLines);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/**
	 * Releases the hold {@link craftingismagic.cim.villager.VillagerDebugEditorHandler#open} put on
	 * the entity - reuses the exact same {@code StopVillagerInteractionC2SPayload}/{@code
	 * VillagerInteractionHandler#stopInteracting} path the normal villager menu already uses, since
	 * both now share the same freeze map. Deliberately overrides {@code close()}, not {@code
	 * removed()}: switching tabs replaces this screen via {@code MinecraftClient#setScreen} directly
	 * (not {@code this.close()}), which only triggers {@code removed()} - so tab-switching correctly
	 * does NOT release the freeze, only an intentional close (the Close button, or Escape) does.
	 */
	@Override
	public void close() {
		ClientPlayNetworking.send(new StopVillagerInteractionC2SPayload(villagerEntityId));
		super.close();
	}

	private record LabeledField(String translationKey, int y) {
	}

	private static final class EditorState {
		String name;
		Species species;
		LifeStage lifeStage;
		int growthTicks;
		PonyGenetics genetics;
		Personality personality;
		int affection;
		RelationshipType relationshipType;
		Item favoriteGift;
		List<String> friendGroup;
		OpenVillagerDebugEditorS2CPayload.Family family;
		OpenVillagerDebugEditorS2CPayload.ChangelingInfo changelingInfo;
	}

	/** A {@link SliderWidget} that shows a translated label + the current value (formatted to 2
	 * decimal places) as its own message, since the genetics floats it edits are all already
	 * 0.0-1.0 - exactly {@link SliderWidget}'s own internal {@code value} range, no conversion
	 * needed. */
	private static final class LabeledSliderWidget extends SliderWidget {
		private final Text label;
		private final DoubleConsumer onChange;

		LabeledSliderWidget(int x, int y, int width, int height, Text label, float initial, DoubleConsumer onChange) {
			super(x, y, width, height, Text.empty(), MathHelper.clamp(initial, 0f, 1f));
			this.label = label;
			this.onChange = onChange;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Text.literal(label.getString() + ": " + String.format(Locale.ROOT, "%.2f", this.value)));
		}

		@Override
		protected void applyValue() {
			onChange.accept(this.value);
		}
	}
}
