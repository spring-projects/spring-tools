package com.example;

import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.wellsfargo.lendingplatform.web.config.TestObjectWithList;

@Component
public class Runner implements CommandLineRunner {

	@Autowired
	TestMap props;
	
	@Override
	public void run(String... arg0) throws Exception {
		p(">>> test map");
		for (Entry<String, TestObjectWithList> e : props.getTestMap().entrySet()) {
			p(e.getKey() + " -> ");
			String[] strings = e.getValue().getStringList();
			if (strings == null) {
				p("  null");
			} else {
				for (String string : strings) {
					p("  '"+string+"'");
				}
			}
		}
	}

	private void p(String string) {
		System.out.println(string);
	}

}
