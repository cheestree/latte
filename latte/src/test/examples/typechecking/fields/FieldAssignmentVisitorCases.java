package typechecking.fields;

import specification.Free;
import specification.Shared;
import specification.Unique;

public class FieldAssignmentVisitorCases {
	@Unique Object uniqueField;
	@Shared Object sharedField;

	void readFields(@Shared FieldAssignmentVisitorCases sharedReceiver) {
		Object firstUniqueRead = this.uniqueField;
		Object secondUniqueRead = this.uniqueField;
		Object sharedRead = sharedReceiver.sharedField;
		Object staticRead = System.out;
	}

	void variableAssignment(@Free Object source) {
		Object target;
		target = source;
	}

	void fieldAssignment(@Free Object source) {
		this.uniqueField = source;
	}
}
