package main;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import effects.*;
  
public class MainGLFW {	
	
	public final static int WIDTH = 400;
	public final static int HEIGHT = 300;
	
	private GLFWErrorCallback errorCallback;
	private GLFWKeyCallback   keyCallback;
	private OpenGLEffect effect;
	
	private long window;
	
	public void run() { 
		try {
			init();
			loop();
			GLFW.glfwDestroyWindow(window);
			keyCallback.release();
		} finally {
			GLFW.glfwTerminate();
			errorCallback.release();
		}
	}
	
	private void init() {
		GLFW.glfwSetErrorCallback(errorCallback = Callbacks.errorCallbackPrint(System.err));
		if ( GLFW.glfwInit() != GL11.GL_TRUE )
			throw new IllegalStateException("Unable to initialize GLFW");
 
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GL11.GL_FALSE);
  
		window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "Hello World!", MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL)
			throw new RuntimeException("Failed to create the GLFW window");
		
		GLFW.glfwSetKeyCallback(window, keyCallback = new GLFWKeyCallback() {
			@Override
			public void invoke(long window, int key, int scancode, int action, int mods) {
				if ( key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE )
					GLFW.glfwSetWindowShouldClose(window, GL11.GL_TRUE); // We will detect this in our rendering loop
			}
		});
				
		GLFW.glfwMakeContextCurrent(window);
		GLFW.glfwSwapInterval(1);
		GLFW.glfwShowWindow(window);
	}
	
	private void loop() {
		GLContext.createFromCurrent();
		GL11.glClearColor(1, 1, 1, 1);
		effect = new Stencil();
		effect.setUp();
		while (GLFW.glfwWindowShouldClose(window) == GL11.GL_FALSE) {
			effect.update(window);
			GLFW.glfwSwapBuffers(window);
			GLFW.glfwPollEvents();
		}
		effect.cleanUp();
	}
 
	public static void main(String[] args) {
		new MainGLFW().run();
	}
 
}