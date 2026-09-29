package com.gieligotchi.ui;

import com.gieligotchi.GieligotchiConfig;
import com.gieligotchi.service.GieligotchiStateService;
import com.gieligotchi.service.PetCatalogue;
import com.gieligotchi.service.SaveCodec;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelScrollTest
{
	@Test public void everyMainPageScrollsVerticallyWhenContentDoesNotFit() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			try
			{
				GieligotchiStateService state = new GieligotchiStateService(null, null,
					new SaveCodec(new Gson()));
				GieligotchiPanel panel = new GieligotchiPanel(state, new PetCatalogue(new Gson()),
					new GieligotchiConfig() {}, new HatchAnimationController(state),
					new CompanionEffectController());
				java.lang.reflect.Field field = GieligotchiPanel.class.getDeclaredField("pageHost");
				field.setAccessible(true);
				JPanel pageHost = (JPanel) field.get(panel);
				int scrollPanes = 0;
				for (Component child : pageHost.getComponents())
				{
					if (!(child instanceof JScrollPane)) { continue; }
					JScrollPane scroll = (JScrollPane) child;
					assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
						scroll.getVerticalScrollBarPolicy());
					assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
						scroll.getHorizontalScrollBarPolicy());
					assertTrue(scroll.isWheelScrollingEnabled());
					scrollPanes++;
				}
				assertEquals("All six main pages should be scrollable", 6, scrollPanes);
			}
			catch (Exception error) { throw new RuntimeException(error); }
		});
	}

	@Test public void shortPanelExposesTheFullHomePageThroughScrolling() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			try
			{
				GieligotchiStateService state = new GieligotchiStateService(null, null,
					new SaveCodec(new Gson()));
				GieligotchiPanel panel = new GieligotchiPanel(state, new PetCatalogue(new Gson()),
					new GieligotchiConfig() {}, new HatchAnimationController(state),
					new CompanionEffectController());
				panel.refresh();
				panel.setSize(242, 420);
				for (int i = 0; i < 5; i++) { layout(panel); }

				java.lang.reflect.Field field = GieligotchiPanel.class.getDeclaredField("pageHost");
				field.setAccessible(true);
				JPanel pageHost = (JPanel) field.get(panel);
				JScrollPane homeScroll = null;
				for (Component child : pageHost.getComponents())
				{
					if (child instanceof JScrollPane && child.isVisible())
					{
						homeScroll = (JScrollPane) child;
						break;
					}
				}
				assertNotNull(homeScroll);
				assertTrue(homeScroll.getVerticalScrollBar().isVisible());
				assertTrue(homeScroll.getVerticalScrollBar().getMaximum()
					> homeScroll.getVerticalScrollBar().getVisibleAmount());
			}
			catch (Exception error) { throw new RuntimeException(error); }
		});
	}

	private static void layout(Container parent)
	{
		parent.doLayout();
		for (Component child : parent.getComponents())
		{
			if (child instanceof Container) { layout((Container) child); }
		}
	}
}
