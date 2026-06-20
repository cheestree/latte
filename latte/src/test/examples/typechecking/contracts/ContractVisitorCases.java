package typechecking.contracts;

import specification.Shared;
import specification.lj.StateRefinement;

public class ContractVisitorCases {
	public ContractVisitorCases(@Shared Object seed) {
	}

	@Shared
	@StateRefinement(from = "value == value")
	Object echo(@Shared Object value) {
		return value;
	}
}
