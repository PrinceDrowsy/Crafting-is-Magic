package craftingismagic.cim.narrative;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.narrative.TaleAst.Node;
import craftingismagic.cim.narrative.TaleAst.TaleFile;

/**
 * Every {@code .tale} file the loaded datapacks provide ({@code data/<namespace>/tales/**.tale}),
 * parsed and validated on each data reload ({@code /reload} included). Files load even when they
 * have problems, so the report can point at them; the runtime decides what to skip.
 */
public final class TaleLibrary {
	public static final String FOLDER = "tales";
	public static final String EXTENSION = ".tale";
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/narrative");

	private static volatile Snapshot current = new Snapshot(Map.of(), Map.of(), List.of());

	private TaleLibrary() {
	}

	/** One reload's worth of tales. Replaced whole, so readers never see a half-loaded library. */
	public record Snapshot(Map<Identifier, TaleFile> files, Map<Identifier, Node> nodes, List<TaleProblem> problems) {
		public long errorCount() {
			return problems.stream().filter(problem -> problem.severity() == TaleProblem.Severity.ERROR).count();
		}

		public long warningCount() {
			return problems.size() - errorCount();
		}
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("cim", "tales");
			}

			@Override
			public void reload(ResourceManager manager) {
				TaleLibrary.reload(manager);
			}
		});
	}

	public static Snapshot get() {
		return current;
	}

	private static void reload(ResourceManager manager) {
		Map<Identifier, String> texts = new LinkedHashMap<>();
		Map<Identifier, String> sources = new LinkedHashMap<>();
		List<TaleProblem> readProblems = new ArrayList<>();
		Map<Identifier, Resource> found = manager.findResources(FOLDER, path -> path.getPath().endsWith(EXTENSION));
		found.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString))).forEach(entry -> {
			Identifier path = entry.getKey();
			Identifier fileId = Identifier.of(path.getNamespace(),
					path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - EXTENSION.length()));
			String source = "data/" + path.getNamespace() + "/" + path.getPath() + " (" + entry.getValue().getPackId() + ")";
			try (BufferedReader reader = entry.getValue().getReader()) {
				texts.put(fileId, reader.lines().collect(Collectors.joining("\n")));
				sources.put(fileId, source);
			} catch (IOException | RuntimeException e) {
				readProblems.add(new TaleProblem(TaleProblem.Severity.ERROR, source, 0, "Couldn't read the file: " + e.getMessage()));
			}
		});

		Snapshot snapshot = build(texts, sources);
		if (!readProblems.isEmpty()) {
			List<TaleProblem> all = new ArrayList<>(readProblems);
			all.addAll(snapshot.problems());
			snapshot = new Snapshot(snapshot.files(), snapshot.nodes(), List.copyOf(all));
		}
		current = snapshot;

		LOGGER.info("Loaded {} tale file(s), {} node(s): {} error(s), {} warning(s)",
				snapshot.files().size(), snapshot.nodes().size(), snapshot.errorCount(), snapshot.warningCount());
		for (TaleProblem problem : snapshot.problems()) {
			if (problem.severity() == TaleProblem.Severity.ERROR) {
				LOGGER.error("{}", problem);
			} else {
				LOGGER.warn("{}", problem);
			}
		}
	}

	/**
	 * Parses and validates a set of files, keyed by file ID. Separate from the reload so it can run
	 * without a game (tests, tools).
	 */
	public static Snapshot build(Map<Identifier, String> texts, Map<Identifier, String> sources) {
		List<TaleProblem> problems = new ArrayList<>();
		Map<Identifier, TaleFile> files = new LinkedHashMap<>();
		for (Map.Entry<Identifier, String> entry : texts.entrySet()) {
			String source = sources.getOrDefault(entry.getKey(), entry.getKey().toString());
			files.put(entry.getKey(), TaleParser.parse(entry.getKey(), source, entry.getValue(), problems));
		}
		TaleValidator.validate(List.copyOf(files.values()), problems);

		Map<Identifier, Node> nodes = new LinkedHashMap<>();
		for (TaleFile file : files.values()) {
			for (Node node : file.nodes()) {
				nodes.putIfAbsent(node.id(), node);
			}
		}
		problems.sort(Comparator.comparing(TaleProblem::source).thenComparingInt(TaleProblem::line));
		return new Snapshot(Collections.unmodifiableMap(files), Collections.unmodifiableMap(nodes), List.copyOf(problems));
	}
}
