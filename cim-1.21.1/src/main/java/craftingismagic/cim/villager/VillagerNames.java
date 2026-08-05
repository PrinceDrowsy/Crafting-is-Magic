package craftingismagic.cim.villager;

import java.util.List;

import net.minecraft.util.math.random.Random;

/**
 * A curated pool of show-accurate pony names, assigned once per villager so they get a visible
 * nametag like players do. Pulled directly from the Equestria name set in the neighboring
 * {@code friendship-comes-alive} project (its own Minecraft Comes Alive-based mod) rather than
 * inventing a new list - mostly canon show background-pony names, plus some in-theme original
 * ones. That source data is split into female/male lists; this mod has no gender system anywhere,
 * so both are merged into one flat pool here rather than building gendered assignment just for
 * flavor text.
 */
public final class VillagerNames {
	private static final List<String> NAMES = List.of(
			"Amber Hoofprint", "Amethyst Sparkler", "Apricot Jam", "Berry Cobbler",
			"Berry Punch", "Blackberry Jam", "Blossom", "Blue Belle",
			"Bon Bon", "Brook", "Brook Gallop", "Bumblesweet",
			"Butter Biscuit", "Buttercup Trot", "Butterscotch", "Carrot Top",
			"Chamomile", "Cheerilee", "Cherry Cordial", "Cherry Jubilee",
			"Chestnut Crumble", "Cinnamon", "Clementine", "Clementine Gallop",
			"Cloudchaser", "Clover", "Clover Stitch", "Coco Pommel",
			"Comet", "Cotton Candy", "Cranberry Tart", "Custard Cobbler",
			"Daffodil Dance", "Daisy", "Dandy Lion", "Derpy Hooves",
			"Diamond Tiara", "Ember", "Fifi", "Firefly",
			"Fizzy", "Flitter", "Foxglove Charm", "Frost",
			"Frosty Muzzle", "Ginger Snap", "Glory", "Golden Withers",
			"Gusty", "Hazelnut Praline", "Heather", "Honeycomb Twist",
			"Iris Waltz", "Ivy Bridle", "Jasmine Canter", "Juniper",
			"Kimono", "Larkspur Chime", "Lavender Sachet", "Lemon Hearts",
			"Lily Valley", "Lyra Heartstrings", "Majesty", "Maple",
			"Maple Canter", "Marigold", "Marigold Petal", "Marmalade",
			"Marmalade Toast", "Meadow", "Medley", "Minty",
			"Minuette", "Misty Fetlock", "Moondancer", "Nutmeg",
			"Octavia Melody", "Orchid Sway", "Peaches Cobbler", "Petunia Bounce",
			"Plum Preserve", "Poppy", "Primrose Canter", "Pumpkin Spice",
			"Raspberry Ripple", "Ribbon Wishes", "Roseluck", "Rosy Saddlebag",
			"Saffron", "Sandy Trotter", "Sassy Saddles", "Silver Spoon",
			"Sky Wishes", "Snapdragon Bell", "Sorrel", "Sorrel Trot",
			"Sparkler", "Star Catcher", "Sundance", "Sunny Bridle",
			"Sunny Daze", "Surprise", "Sweet Stuff", "Sweetberry",
			"Thistle", "Thistle Mane", "Toffee", "Toffee Crumble",
			"Toola-Roola", "Tulip Twirl", "Twinkleshine", "Vinyl Scratch",
			"Violet Posy", "Whoopsy-Daisy", "Wind Whistler", "Wisteria Bloom",
			"Wysteria", "Barley Malt", "Basil", "Birch Gallop",
			"Biscuit", "Bracken Trail", "Braeburn", "Bramble Thorn",
			"Butterscotch Trot", "Canter", "Caramel", "Cedar Trot",
			"Chestnut Canter", "Chipper Trot", "Clove", "Cobbler",
			"Corn Husk", "Dapper Hoofbeat", "Dashing Gait", "Doctor Whooves",
			"Fancy Pants", "Fernwood Trot", "Filthy Rich", "Flint Horseshoe",
			"Gallop", "Hazel Cobbler", "Hoity Toity", "Hollow Hoofbeat",
			"Honest Toil", "Humble Trot", "Merry Jest", "Mossy Stone",
			"Oatmeal Crumble", "Peanut Crumble", "Pecan", "Plucky Hoofbeat",
			"Proper Trot", "Rocky Canter", "Rugged Withers", "Rusty Bridle",
			"Sandy Fetlock", "Snails", "Snappy Trot", "Snips",
			"Solid Hoofbeat", "Stone Withers", "Stormy Gallop", "Sturdy Fetlock",
			"Thunderlane", "Timber Canter", "Trouble Shoes", "Wheat Sheaf",
			"Windy Gallop", "Witty Remark");

	private VillagerNames() {
	}

	public static String random(Random random) {
		return NAMES.get(random.nextInt(NAMES.size()));
	}
}
