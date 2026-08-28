package org.springframework.ide.vscode.xml.namespaces.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Date;


/**
 * Poor man's logger with a default implementation writes log output for jdt.ls extension into a predictable location.
 */
public interface Logger {
	
	public static class NullLogger implements Logger {

		@Override
		public void log(String message) {
		}

		@Override
		public void log(Exception e) {
		}

	}
	public static Logger DEFAULT //pick one of the two below. Probably should use NullLogger in 'production'.
		= new NullLogger();
		//= new DefaultLogger(false);

	public static class DefaultLogger implements Logger {
		private PrintWriter printwriter;
		public DefaultLogger(boolean USE_SYS_ERR) {
			if (USE_SYS_ERR) {
				printwriter = new PrintWriter(System.err);
			} else {
				File file = new File(System.getProperty("java.io.tmpdir"));
				file = new File(file, "stsxmlls.log");
				try {
					printwriter = new PrintWriter(new FileOutputStream(file), true);
					log("======== "+new Date()+" =======");
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		@Override
		public void log(String message) {
			printwriter.println(message);
			printwriter.flush();
		}

		@Override
		public void log(Exception e) {
			e.printStackTrace(printwriter);
		}
	}


	public static class TestLogger extends DefaultLogger {

		private Exception firstError;

		public TestLogger() {
			super(true);
		}
		
		@Override
		public void log(Exception e) {
			super.log(e);
			if (firstError != null) {
				firstError = e;
			}
		}
		
		public void assertNoErrors() throws Exception {
			if (firstError != null) {
				throw firstError;
			}
		}
	}

	void log(String message);
	void log(Exception e);
	
}
