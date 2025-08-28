package example.application;

public class ClassWithInnerRecordImplementingInterface {
	
	public record InnerRecord(String someProp) implements RandomInterface {
		
	}

}
