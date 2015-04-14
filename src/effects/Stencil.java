package effects;

import main.GLUtils;

import org.lwjgl.opengl.GL11;

public class Stencil implements OpenGLEffect {

	int texture;
	
	@Override
	public void setUp() {
		// using fixed function pipeline
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glOrtho(-4/3f, 4/3f, -1, +1, -1, +1);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		
		// clear stencil
	    GL11.glClearStencil(0);
		
		// load cat texture
		texture = GLUtils.createTexture("pusheen.png");
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
	}

	float t = 0;
	
	@Override
	public void update(long window) {
		GL11.glClearColor(0, 0, 0, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
		
		GL11.glColorMask(false, false, false, false);
		GL11.glEnable(GL11.GL_STENCIL_TEST);
	    GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
	    GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
		GL11.glPushMatrix();
		t += 1f;
		GL11.glTranslated(Math.cos(Math.toRadians(t))*0.5, Math.sin(Math.toRadians(t)*2)*0.5, 0);
		GL11.glRotatef(t, 0, 0, 1);
		GL11.glBegin(GL11.GL_TRIANGLES);
		GL11.glVertex2f(-0.35f, -0.35f);
		GL11.glVertex2f(0.35f, -0.35f);
		GL11.glVertex2f(0, 0.25f);
		GL11.glEnd();
		GL11.glPopMatrix();
		
		// enable stencil test
		//GL11.glDisable(GL11.GL_STENCIL_TEST);
		GL11.glStencilFunc(GL11.GL_EQUAL, 1, 1);
		GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
		GL11.glColorMask(true, true, true, true);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0, 0);GL11.glVertex2f(-1, -1);
		GL11.glTexCoord2f(1, 0);GL11.glVertex2f(1, -1);
		GL11.glTexCoord2f(1, 1);GL11.glVertex2f(1, 1);
		GL11.glTexCoord2f(0, 1);GL11.glVertex2f(-1, 1);
		GL11.glEnd();
	}

	@Override
	public void cleanUp() {
		GL11.glDeleteTextures(texture);
	}

}
