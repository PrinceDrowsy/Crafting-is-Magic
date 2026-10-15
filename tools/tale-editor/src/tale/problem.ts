// Something wrong (or worth a look) in a .tale file, pinned to a line - mirrors TaleProblem.java.

export type Severity = "error" | "warning";

export interface Problem {
  severity: Severity;
  source: string;
  line: number;
  message: string;
}

export function describe(problem: Problem): string {
  return `${problem.source}:${problem.line}: ${problem.severity}: ${problem.message}`;
}
