package tim.prune;

import java.awt.event.WindowAdapter;
import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.WindowConstants;

import tim.prune.gui.DetailsDisplay;
import tim.prune.gui.IconManager;
import tim.prune.gui.MenuManager;
import tim.prune.gui.ProfileChart;
import tim.prune.gui.SelectorDisplay;
import tim.prune.gui.StatusBar;
import tim.prune.gui.Viewport;
import tim.prune.gui.map.MapCanvas;

/**
 * Tool to visualize, edit, convert and prune GPS data
 * Please see the included readme.txt or http://activityworkshop.net
 * This software is copyright activityworkshop.net and made available through the Gnu GPL
 */
public class GpsPruner
{
	/** Version number of application, used in about screen and for version check */
	public static final String VERSION_NUMBER = "8";
	/** Build number, just used for about screen */
	public static final String BUILD_NUMBER = "155";
	/** Static reference to App object */
	private static App APP = null;


	/**
	 * Main method
	 * @param args command line arguments
	 */
	public static void main(String[] args)
	{
		Locale locale = null;
		String localeCode = null;
		String langFilename = null;
		String configFilename = null;
		ArrayList<File> dataFiles = new ArrayList<File>();
		boolean showUsage = false;
		for (int i=0; i<args.length; i++)
		{
			String arg = args[i];
			if (arg.startsWith("--locale="))
			{
				localeCode = arg.substring(9);
				locale = getLanguage(localeCode);
			}
			else if (arg.startsWith("--lang="))
			{
				localeCode = arg.substring(7);
				locale = getLanguage(localeCode);
			}
			else if (arg.startsWith("--langfile="))
			{
				langFilename = arg.substring(11);
			}
			else if (arg.startsWith("--configfile="))
			{
				configFilename = arg.substring(13);
			}
			else if (arg.startsWith("--help")) {
				showUsage = true;
			}
			else
			{
				// Check if a data file has been given
				File f = new File(arg);
				if (f.exists() && f.canRead()) {
					dataFiles.add(f);
				}
				else {
					System.out.println("Unknown parameter '" + arg + "'.");
					showUsage = true;
				}
			}
		}
		if (showUsage) {
			System.out.println("Possible parameters:"
				+ "\n   --configfile=<file> used to specify a configuration file"
				+ "\n   --lang=<code> or --locale=<code>  used to specify language"
				+ "\n   --langfile=<file>   used to specify an alternative language file\n");
		}
		// Initialise configuration if selected
		try
		{
			if (configFilename != null) {
				Config.loadFile(new File(configFilename));
			}
			else {
				Config.loadDefaultFile();
			}
		}
		catch (ConfigException ce) {
			System.err.println("Failed to load config file: " + configFilename);
		}
		if (locale != null) {
			// Make sure Config holds chosen language
			Config.setConfigString(Config.KEY_LANGUAGE_CODE, localeCode);
		}
		else {
			// Set locale according to Config's language property
			String configLang = Config.getConfigString(Config.KEY_LANGUAGE_CODE);
			if (configLang != null) {
				Locale configLocale = getLanguage(configLang);
				if (configLocale != null) {locale = configLocale;}
			}
		}
		I18nManager.init(locale);
		if (langFilename != null) {
			try {
				I18nManager.addLanguageFile(langFilename);
			}
			catch (FileNotFoundException fnfe) {
				System.err.println("Failed to load language file: " + langFilename);
			}
		}
		// Set up the window and go
		launch(dataFiles);
	}


	/**
	 * Choose a locale based on the given code
	 * @param inString code for locale
	 * @return Locale object if available, otherwise null
	 */
	private static Locale getLanguage(String inString)
	{
		if (inString.length() == 2)
		{
			return new Locale(inString);
		}
		else if (inString.length() == 5)
		{
			return new Locale(inString.substring(0, 2), inString.substring(3));
		}
		System.out.println("Unrecognised locale '" + inString
			+ "' - value should be eg 'DE' or 'DE_ch'");
		return null;
	}


	/**
	 * Launch the main application
	 * @param inDataFiles list of data files to load on startup
	 */
	private static void launch(ArrayList<File> inDataFiles)
	{
		JFrame frame = new JFrame("Prune");
		APP = new App(frame);

		// make menu
		MenuManager menuManager = new MenuManager(APP, APP.getTrackInfo());
		frame.setJMenuBar(menuManager.createMenuBar());
		APP.setMenuManager(menuManager);
		UpdateMessageBroker.addSubscriber(menuManager);
		// Make toolbar for buttons
		JToolBar toolbar = menuManager.createToolBar();

		// Make main GUI components and add as listeners
		SelectorDisplay leftPanel = new SelectorDisplay(APP.getTrackInfo());
		UpdateMessageBroker.addSubscriber(leftPanel);
		DetailsDisplay rightPanel = new DetailsDisplay(APP.getTrackInfo());
		UpdateMessageBroker.addSubscriber(rightPanel);
		MapCanvas mapDisp = new MapCanvas(APP, APP.getTrackInfo());
		UpdateMessageBroker.addSubscriber(mapDisp);
		Viewport viewport = new Viewport(mapDisp);
		APP.setViewport(viewport);
		ProfileChart profileDisp = new ProfileChart(APP.getTrackInfo());
		UpdateMessageBroker.addSubscriber(profileDisp);
		StatusBar statusBar = new StatusBar();
		UpdateMessageBroker.addSubscriber(statusBar);
		UpdateMessageBroker.informSubscribers("Prune v" + VERSION_NUMBER);

		// Arrange in the frame using split panes
		JSplitPane midPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapDisp, profileDisp);
		midPane.setResizeWeight(1.0); // allocate as much space as poss to map
		JSplitPane triplePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, midPane, rightPanel);
		triplePane.setResizeWeight(1.0); // allocate as much space as poss to map

		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(toolbar, BorderLayout.NORTH);
		frame.getContentPane().add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel,
			triplePane), BorderLayout.CENTER);
		frame.getContentPane().add(statusBar, BorderLayout.SOUTH);

		// add closing listener
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				APP.exit();
			}
		});
		// Avoid automatically shutting down if window closed
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		// set icon
		try {
			frame.setIconImage(IconManager.getImageIcon(IconManager.WINDOW_ICON).getImage());
		}
		catch (Exception e) {} // ignore

		// finish off and display frame
		frame.pack();
		frame.setSize(650, 450);
		frame.setVisible(true);
		// Set position of map/profile splitter
		midPane.setDividerLocation(0.75);

		// Finally, give the files to load to the App
		APP.loadDataFiles(inDataFiles);
	}
}
