package demo;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "star-wars")
public class SWCharacter {

	private String id;

	@Field("firstname")
	private String name;

	private String homePlanet;

	public SWCharacter(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getHomePlanet() {
		return homePlanet;
	}

}
