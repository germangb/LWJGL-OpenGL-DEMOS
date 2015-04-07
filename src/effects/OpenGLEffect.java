package effects;

public interface OpenGLEffect {

	public void setUp ();
	
	public void update (long window);
	
	public void cleanUp();
	
}
