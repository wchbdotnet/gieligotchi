package com.gieligotchi.ui;

import com.gieligotchi.GieligotchiConfig;
import com.gieligotchi.service.GieligotchiStateService;
import com.gieligotchi.service.PetCatalogue;
import com.gieligotchi.service.SaveCodec;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuideLayoutTest
{
	@Test public void saveGuideFitsAtBothSupportedReadingScales() throws Exception
	{
		for (int scale : new int[]{120, 140})
		{
			SwingUtilities.invokeAndWait(() -> {
				try
				{
					GieligotchiStateService state = new GieligotchiStateService(null, null, new SaveCodec(new Gson()));
					GieligotchiPanel panel = new GieligotchiPanel(state, new PetCatalogue(new Gson()),
						new GieligotchiConfig() { @Override public int textScale() { return scale; } },
						new HatchAnimationController(state), new CompanionEffectController());
					panel.refresh();
					java.lang.reflect.Method show = GieligotchiPanel.class.getDeclaredMethod("showPage", String.class);
					show.setAccessible(true);
					show.invoke(panel, "guide");
					panel.setSize(242, 1000);
					for (int i = 0; i < 5; i++) { layout(panel); }
					java.lang.reflect.Field field = GieligotchiPanel.class.getDeclaredField("guide");
					field.setAccessible(true);
					JPanel guide = (JPanel) field.get(panel);
					assertTrue(hasSaveButton(guide));
					BufferedImage image = new BufferedImage(242, 1000, BufferedImage.TYPE_INT_ARGB);
					Graphics2D graphics = image.createGraphics();
					panel.paint(graphics);
					graphics.dispose();
					Path output = Path.of("build", "reports", "guide-" + scale + ".png");
					Files.createDirectories(output.getParent());
					ImageIO.write(image, "png", output.toFile());
					checkText(guide);
				}
				catch (Exception error) { throw new RuntimeException(error); }
			});
		}
	}

	private static void layout(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } }
	}
	private static boolean hasSaveButton(Container parent)
	{
		for (Component child : parent.getComponents())
		{ if (child instanceof JButton && "Save & sync".equals(((JButton) child).getText())) { return true; } }
		return false;
	}
	private static void checkText(Container parent) throws Exception
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JTextArea)
			{
				JTextArea text = (JTextArea) child;
				java.awt.Rectangle end = text.modelToView(text.getDocument().getLength());
				assertNotNull(end);
				assertTrue("Clipped guide copy (" + text.getSize() + ", end " + end + "): " + text.getText(), end.y + end.height <= text.getHeight());
			}
			if (child instanceof Container) { checkText((Container) child); }
		}
	}
}
