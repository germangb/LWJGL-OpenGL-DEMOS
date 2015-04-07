package main;
import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import effects.Blur;
import effects.OpenGLEffect;


public class Main {

	public final static int WIDTH = 400;
	public final static int HEIGHT = 300;
	
	private static long getGlobalTime () {
		long now = (long) (Sys.getTime() * 1000 / Sys.getTimerResolution());
		return now;
	}
	
	public static void main (String[] args) {
		
		try {
			Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.setResizable(false);
			Display.create();
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
		
		int ticks = 0;
		long lastTime = getGlobalTime();
		
		OpenGLEffect effect = new Blur();
		effect.setUp();
		while (!Display.isCloseRequested()) {
			long now = getGlobalTime();
			if (now-lastTime > 1000) {
				System.out.println(ticks+" FPS");
				ticks = 0;
				lastTime = now;
			} else ++ticks;
			effect.update();
			Display.update();
			Display.sync(60);
		}
		effect.cleanUp();
		Display.destroy();
		
	}
	
}
