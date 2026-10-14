package craftingismagic.cim.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import craftingismagic.cim.client.accessibility.AccessibilityConfig;
import craftingismagic.cim.client.dialogue.DialogueConfig;
import craftingismagic.cim.client.mood.MomentConfig;
import craftingismagic.cim.debug.DebugConfig;
import craftingismagic.cim.spawn.VillageSpawnConfig;
import craftingismagic.cim.villager.LifeStageConfig;

/**
 * The in-game settings screen, built with YACL. Optional: only ever loaded when YACL is installed
 * (see {@link CimModMenu}), never a requirement - the mod's own JSON configs always work without it.
 * This screen just edits those same config objects and writes them back.
 */
public final class CimConfigScreen {
	private CimConfigScreen() {
	}

	public static Screen create(Screen parent) {
		AccessibilityConfig accessibility = AccessibilityConfig.get();
		AccessibilityConfig accessibilityDefaults = new AccessibilityConfig();
		DialogueConfig dialogue = DialogueConfig.get();
		DialogueConfig dialogueDefaults = new DialogueConfig();
		MomentConfig moments = MomentConfig.get();
		MomentConfig momentDefaults = new MomentConfig();
		VillageSpawnConfig spawn = VillageSpawnConfig.get();
		LifeStageConfig lifeStages = LifeStageConfig.get();
		VillageSpawnConfig spawnDefaults = new VillageSpawnConfig();
		LifeStageConfig lifeStageDefaults = new LifeStageConfig();
		DebugConfig debug = DebugConfig.get();
		DebugConfig debugDefaults = new DebugConfig();

		return YetAnotherConfigLib.createBuilder()
				.title(Text.translatable("config.cim.title"))
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("config.cim.category.accessibility"))
						.option(Option.<Boolean>createBuilder()
								.name(Text.translatable("config.cim.dyslexic_font"))
								.description(OptionDescription.of(Text.translatable("config.cim.dyslexic_font.desc")))
								.binding(accessibilityDefaults.dyslexicFont, () -> accessibility.dyslexicFont, value -> accessibility.dyslexicFont = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Float>createBuilder()
								.name(Text.translatable("config.cim.text_scale"))
								.description(OptionDescription.of(Text.translatable("config.cim.text_scale.desc")))
								.binding(accessibilityDefaults.textScale, () -> accessibility.textScale, value -> accessibility.textScale = value)
								.controller(option -> FloatSliderControllerBuilder.create(option)
										.range(AccessibilityConfig.MIN_TEXT_SCALE, AccessibilityConfig.MAX_TEXT_SCALE)
										.step(0.05f))
								.build())
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("config.cim.category.conversations"))
						.option(Option.<DialogueConfig.VoiceLines>createBuilder()
								.name(Text.translatable("config.cim.voice_lines"))
								.description(OptionDescription.of(Text.translatable("config.cim.voice_lines.desc")))
								.binding(dialogueDefaults.voiceLines, () -> dialogue.voiceLines, value -> dialogue.voiceLines = value)
								.controller(option -> EnumControllerBuilder.create(option)
										.enumClass(DialogueConfig.VoiceLines.class)
										.formatValue(value -> Text.translatable("config.cim.voice_lines." + value.name().toLowerCase(java.util.Locale.ROOT))))
								.build())
						.option(Option.<DialogueConfig.RevealSpeed>createBuilder()
								.name(Text.translatable("config.cim.reveal_speed"))
								.description(OptionDescription.of(Text.translatable("config.cim.reveal_speed.desc")))
								.binding(dialogueDefaults.revealSpeed, () -> dialogue.revealSpeed, value -> dialogue.revealSpeed = value)
								.controller(option -> EnumControllerBuilder.create(option)
										.enumClass(DialogueConfig.RevealSpeed.class)
										.formatValue(value -> Text.translatable("config.cim.reveal_speed." + value.name().toLowerCase(java.util.Locale.ROOT))))
								.build())
						.option(Option.<Boolean>createBuilder()
								.name(Text.translatable("config.cim.reveal_sound"))
								.description(OptionDescription.of(Text.translatable("config.cim.reveal_sound.desc")))
								.binding(dialogueDefaults.revealSound, () -> dialogue.revealSound, value -> dialogue.revealSound = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("config.cim.category.moments"))
						.option(Option.<Boolean>createBuilder()
								.name(Text.translatable("config.cim.moments_enabled"))
								.description(OptionDescription.of(Text.translatable("config.cim.moments_enabled.desc")))
								.binding(momentDefaults.enabled, () -> moments.enabled, value -> moments.enabled = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Float>createBuilder()
								.name(Text.translatable("config.cim.moments_intensity"))
								.description(OptionDescription.of(Text.translatable("config.cim.moments_intensity.desc")))
								.binding(momentDefaults.intensity, () -> moments.intensity, value -> moments.intensity = value)
								.controller(option -> FloatSliderControllerBuilder.create(option).range(0f, 1f).step(0.05f))
								.build())
						.option(Option.<Boolean>createBuilder()
								.name(Text.translatable("config.cim.moments_music"))
								.description(OptionDescription.of(Text.translatable("config.cim.moments_music.desc")))
								.binding(momentDefaults.music, () -> moments.music, value -> moments.music = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.option(Option.<Boolean>createBuilder()
								.name(Text.translatable("config.cim.moments_sound_dip"))
								.description(OptionDescription.of(Text.translatable("config.cim.moments_sound_dip.desc")))
								.binding(momentDefaults.soundDip, () -> moments.soundDip, value -> moments.soundDip = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("config.cim.category.world"))
						.option(Option.<Boolean>createBuilder()
								.name(Text.translatable("config.cim.spawn_in_village"))
								.description(OptionDescription.of(Text.translatable("config.cim.spawn_in_village.desc")))
								.binding(spawnDefaults.spawnInVillage, () -> spawn.spawnInVillage, value -> spawn.spawnInVillage = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("config.cim.category.life_stages"))
						.option(Option.<Integer>createBuilder()
								.name(Text.translatable("config.cim.ticks_per_year"))
								.description(OptionDescription.of(Text.translatable("config.cim.ticks_per_year.desc")))
								.binding(lifeStageDefaults.ticksPerYear, () -> lifeStages.ticksPerYear, value -> lifeStages.ticksPerYear = value)
								.controller(option -> IntegerFieldControllerBuilder.create(option).min(1))
								.build())
						.option(yearsOption("config.cim.baby_years", lifeStageDefaults.babyDurationYears,
								() -> lifeStages.babyDurationYears, value -> lifeStages.babyDurationYears = value))
						.option(yearsOption("config.cim.child_years", lifeStageDefaults.childDurationYears,
								() -> lifeStages.childDurationYears, value -> lifeStages.childDurationYears = value))
						.option(yearsOption("config.cim.teenager_years", lifeStageDefaults.teenagerDurationYears,
								() -> lifeStages.teenagerDurationYears, value -> lifeStages.teenagerDurationYears = value))
						.build())
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("config.cim.category.debug"))
						.option(Option.<Boolean>createBuilder()
								.name(Text.translatable("config.cim.debug_enabled"))
								.description(OptionDescription.of(Text.translatable("config.cim.debug_enabled.desc")))
								.binding(debugDefaults.enabled, () -> debug.enabled, value -> debug.enabled = value)
								.controller(TickBoxControllerBuilder::create)
								.build())
						.build())
				.save(() -> {
					accessibility.save();
					dialogue.save();
					moments.save();
					spawn.save();
					lifeStages.save();
					debug.save();
				})
				.build()
				.generateScreen(parent);
	}

	private static Option<Float> yearsOption(String key, float defaultValue,
			java.util.function.Supplier<Float> getter, java.util.function.Consumer<Float> setter) {
		return Option.<Float>createBuilder()
				.name(Text.translatable(key))
				.description(OptionDescription.of(Text.translatable("config.cim.stage_years.desc")))
				.binding(defaultValue, getter, setter)
				.controller(option -> FloatFieldControllerBuilder.create(option).min(0.01f))
				.build();
	}
}
