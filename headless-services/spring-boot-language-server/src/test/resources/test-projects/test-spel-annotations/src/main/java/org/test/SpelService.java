package org.test;

import org.springframework.stereotype.Service;

@Service
public class SpelService {

	public boolean isValid(String version) {
		return version != null && !version.isBlank();
	}

}
