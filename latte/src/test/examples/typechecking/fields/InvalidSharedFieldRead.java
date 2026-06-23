package typechecking.fields;

import specification.Shared;
import specification.Unique;

public class InvalidSharedFieldRead {
	@Unique Object uniqueField;

	void read(@Shared InvalidSharedFieldRead other) {
		Object value = other.uniqueField;
	}
}
