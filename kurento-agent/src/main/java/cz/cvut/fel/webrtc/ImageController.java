package cz.cvut.fel.webrtc;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Controller
public class ImageController {
	@RequestMapping(value = "names/{userName}", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<InputStreamResource> downloadUserAvatarImage(@PathVariable String userName) throws IOException {
		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        final ByteArrayOutputStream output;
        Font font = new Font("Arial", Font.PLAIN, 25);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        //int width = fm.stringWidth(userName);
        //int height = fm.getHeight();
        g2d.dispose();

        img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setFont(font);
        fm = g2d.getFontMetrics();
        g2d.setColor(Color.MAGENTA);
        g2d.drawString(userName, (320 - fm.stringWidth(userName)) / 2, 232);
        g2d.dispose();
    	output = new ByteArrayOutputStream() {
    	    @Override
    	    public synchronized byte[] toByteArray() {
    	        return this.buf;
    	    }
    	};
    	
        ImageIO.write(img, "png", output);

        //ByteArrayInputStream is = new ByteArrayInputStream(output.toByteArray(), 0, output.size());
        InputStream is = new ByteArrayInputStream(output.toByteArray());
	    return ResponseEntity.ok()
	            .contentLength(output.size())
	            .contentType(MediaType.parseMediaType(MediaType.IMAGE_PNG_VALUE))
	            .body(new InputStreamResource(is));
	}
	
	
}
