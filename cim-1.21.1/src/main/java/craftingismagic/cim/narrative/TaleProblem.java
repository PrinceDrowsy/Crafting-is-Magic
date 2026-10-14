package craftingismagic.cim.narrative;

/**
 * Something wrong (or worth a look) in a {@code .tale} file, always pinned to a line. Errors stop the
 * affected piece from being used; warnings don't (an unreached node might be an entry point).
 */
public record TaleProblem(Severity severity, String source, int line, String message) {
	public enum Severity {
		ERROR,
		WARNING
	}

	@Override
	public String toString() {
		return source + ":" + line + ": " + severity.name().toLowerCase(java.util.Locale.ROOT) + ": " + message;
	}
}
