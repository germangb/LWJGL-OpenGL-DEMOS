package resources;

import java.io.InputStream;

public class Resources {

	public static InputStream get (String res) {
		return Resources.class.getResourceAsStream(res);
	}
	
}
