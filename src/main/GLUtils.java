package main;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import resources.Resources;
import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

public class GLUtils {

	public static int createTexture (String res) {
		InputStream is = null;
		ByteBuffer rgb = null;
		PNGDecoder png = null;
		try {
			// load random texture
			is = Resources.get(res);
			png = new PNGDecoder(is);
			rgb = BufferUtils.createByteBuffer(png.getWidth()*png.getHeight()*4);
			png.decodeFlipped(rgb, png.getWidth()*4, Format.RGBA);
			rgb.position(0);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, png.getWidth(), png.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgb);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		return id;
	}
	
}
