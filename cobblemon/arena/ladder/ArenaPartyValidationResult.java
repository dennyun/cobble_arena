package cobblemon.arena.ladder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArenaPartyValidationResult {
   private final boolean valid;
   private final List<String> problems;

   private ArenaPartyValidationResult(boolean valid, List<String> problems) {
      this.valid = valid;
      this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
   }

   public static ArenaPartyValidationResult success() {
      return new ArenaPartyValidationResult(true, List.of());
   }

   public static ArenaPartyValidationResult failure(List<String> problems) {
      return new ArenaPartyValidationResult(false, problems);
   }

   public boolean isValid() {
      return this.valid;
   }

   public List<String> getProblems() {
      return this.problems;
   }

   public String getPrimaryProblem() {
      return this.problems.isEmpty() ? "Unknown ladder validation failure." : this.problems.get(0);
   }
}
