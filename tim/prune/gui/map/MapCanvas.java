package tim.prune.gui.map;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import tim.prune.App;
import tim.prune.DataSubscriber;
import tim.prune.I18nManager;
import tim.prune.data.DoubleRange;
import tim.prune.data.Selection;
import tim.prune.data.Track;
import tim.prune.data.TrackInfo;
import tim.prune.gui.IconManager;

/**
 * Class for the map canvas, to display a background map and draw on it
 */
public class MapCanvas extends JPanel implements MouseListener, MouseMotionListener, DataSubscriber,
	KeyListener, MouseWheelListener
{
	/** App object for callbacks */
	private App _app = null;
	/** Track object */
	private Track _track = null;
	/** Selection object */
	private Selection _selection = null;
	/** Previously selected point */
	private int _prevSelectedPoint = -1;
	/** Tile cacher */
	private MapTileCacher _tileCacher = new MapTileCacher(this);
	/** Image to display */
	private BufferedImage _mapImage = null;
	/** Slider for transparency */
	private JSlider _transparencySlider = null;
	/** Checkbox for maps */
	private JCheckBox _mapCheckBox = null;
	/** Checkbox for autopan */
	private JCheckBox _autopanCheckBox = null;
	/** Checkbox for connecting track points */
	private JCheckBox _connectCheckBox = null;
	/** Right-click popup menu */
	private JPopupMenu _popup = null;
	/** Top component panel */
	private JPanel _topPanel = null;
	/** Side component panel */
	private JPanel _sidePanel = null;
	/* Data */
	private DoubleRange _latRange = null, _lonRange = null;
	private DoubleRange _xRange = null, _yRange = null;
	private boolean _recalculate = false;
	/** Flag to check bounds on next paint */
	private boolean _checkBounds = false;
	/** Map position */
	private MapPosition _mapPosition = null;
	/** x coordinate of drag from point */
	private int _dragFromX = -1;
	/** y coordinate of drag from point */
	private int _dragFromY = -1;
	/** Flag set to true for right-click dragging */
	private boolean _zoomDragging = false;
	/** x coordinate of drag to point */
	private int _dragToX = -1;
	/** y coordinate of drag to point */
	private int _dragToY = -1;
	/** x coordinate of popup menu */
	private int _popupMenuX = -1;
	/** y coordinate of popup menu */
	private int _popupMenuY = -1;
	/** Flag to prevent showing too often the error message about loading maps */
	private boolean _shownOsmErrorAlready = false;

	/** Constant for click sensitivity when selecting nearest point */
	private static final int CLICK_SENSITIVITY = 10;
	/** Constant for pan distance from key presses */
	private static final int PAN_DISTANCE = 20;
	/** Constant for pan distance from autopan */
	private static final int AUTOPAN_DISTANCE = 75;

	// Colours
	private static final Color COLOR_BG         = Color.WHITE;
	private static final Color COLOR_POINT      = Color.BLUE;
	private static final Color COLOR_CURR_RANGE = Color.GREEN;
	private static final Color COLOR_CROSSHAIRS = Color.RED;
	private static final Color COLOR_WAYPT_NAME = Color.BLACK;
	private static final Color COLOR_PHOTO_PT   = Color.ORANGE;


	/**
	 * Constructor
	 * @param inApp App object for callbacks
	 * @param inTrackInfo track info object
	 */
	public MapCanvas(App inApp, TrackInfo inTrackInfo)
	{
		_app = inApp;
		_track = inTrackInfo.getTrack();
		_selection = inTrackInfo.getSelection();
		_mapPosition = new MapPosition();
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		addKeyListener(this);

		// Make listener for changes to controls
		ItemListener itemListener = new ItemListener() {
			public void itemStateChanged(ItemEvent e)
			{
				_recalculate = true;
				repaint();
			}
		};
		// Make special listener for changes to map checkbox
		ItemListener mapCheckListener = new ItemListener() {
			public void itemStateChanged(ItemEvent e)
			{
				_tileCacher.clearAll();
				_recalculate = true;
				repaint();
			}
		};
		_topPanel = new JPanel();
		_topPanel.setLayout(new FlowLayout());
		_topPanel.setOpaque(false);
		// Make slider for transparency
		_transparencySlider = new JSlider(0, 5, 0);
		_transparencySlider.setPreferredSize(new Dimension(100, 20));
		_transparencySlider.setMajorTickSpacing(1);
		_transparencySlider.setSnapToTicks(true);
		_transparencySlider.setOpaque(false);
		_transparencySlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e)
			{
				_recalculate = true;
				repaint();
			}
		});
		_transparencySlider.setFocusable(false); // stop slider from stealing keyboard focus
		_topPanel.add(_transparencySlider);
		// Add checkbox button for enabling maps or not
		_mapCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.MAP_BUTTON), false);
		_mapCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.MAP_BUTTON_ON));
		_mapCheckBox.setOpaque(false);
		_mapCheckBox.setToolTipText(I18nManager.getText("menu.map.showmap"));
		_mapCheckBox.addItemListener(mapCheckListener);
		_mapCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_mapCheckBox);
		// Add checkbox button for enabling autopan or not
		_autopanCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.AUTOPAN_BUTTON), true);
		_autopanCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.AUTOPAN_BUTTON_ON));
		_autopanCheckBox.setOpaque(false);
		_autopanCheckBox.setToolTipText(I18nManager.getText("menu.map.autopan"));
		_autopanCheckBox.addItemListener(itemListener);
		_autopanCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_autopanCheckBox);
		// Add checkbox button for connecting points or not
		_connectCheckBox = new JCheckBox(IconManager.getImageIcon(IconManager.POINTS_DISCONNECTED_BUTTON), true);
		_connectCheckBox.setSelectedIcon(IconManager.getImageIcon(IconManager.POINTS_CONNECTED_BUTTON));
		_connectCheckBox.setOpaque(false);
		_connectCheckBox.setToolTipText(I18nManager.getText("menu.map.connect"));
		_connectCheckBox.addItemListener(itemListener);
		_connectCheckBox.setFocusable(false); // stop button from stealing keyboard focus
		_topPanel.add(_connectCheckBox);

		// Add zoom in, zoom out buttons
		_sidePanel = new JPanel();
		_sidePanel.setLayout(new BoxLayout(_sidePanel, BoxLayout.Y_AXIS));
		_sidePanel.setOpaque(false);
		JButton zoomInButton = new JButton(IconManager.getImageIcon(IconManager.ZOOM_IN_BUTTON));
		zoomInButton.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		zoomInButton.setContentAreaFilled(false);
		zoomInButton.setToolTipText(I18nManager.getText("menu.map.zoomin"));
		zoomInButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomIn();
			}
		});
		zoomInButton.setFocusable(false); // stop button from stealing keyboard focus
		_sidePanel.add(zoomInButton);
		JButton zoomOutButton = new JButton(IconManager.getImageIcon(IconManager.ZOOM_OUT_BUTTON));
		zoomOutButton.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		zoomOutButton.setContentAreaFilled(false);
		zoomOutButton.setToolTipText(I18nManager.getText("menu.map.zoomout"));
		zoomOutButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomOut();
			}
		});
		zoomOutButton.setFocusable(false); // stop button from stealing keyboard focus
		_sidePanel.add(zoomOutButton);

		// add control panels to this one
		setLayout(new BorderLayout());
		_topPanel.setVisible(false);
		_sidePanel.setVisible(false);
		add(_topPanel, BorderLayout.NORTH);
		add(_sidePanel, BorderLayout.WEST);
		// Make popup menu
		makePopup();
	}


	/**
	 * Make the popup menu for right-clicking the map
	 */
	private void makePopup()
	{
		_popup = new JPopupMenu();
		JMenuItem zoomInItem = new JMenuItem(I18nManager.getText("menu.map.zoomin"));
		zoomInItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomIn();
			}});
		zoomInItem.setEnabled(true);
		_popup.add(zoomInItem);
		JMenuItem zoomOutItem = new JMenuItem(I18nManager.getText("menu.map.zoomout"));
		zoomOutItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomOut();
			}});
		zoomOutItem.setEnabled(true);
		_popup.add(zoomOutItem);
		JMenuItem zoomFullItem = new JMenuItem(I18nManager.getText("menu.map.zoomfull"));
		zoomFullItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				zoomToFit();
				_recalculate = true;
				repaint();
			}});
		zoomFullItem.setEnabled(true);
		_popup.add(zoomFullItem);
		// new point option
		JMenuItem newPointItem = new JMenuItem(I18nManager.getText("menu.map.newpoint"));
		newPointItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				_app.createPoint(MapUtils.getLatitudeFromY(_mapPosition.getYFromPixels(_popupMenuY, getHeight())),
					MapUtils.getLongitudeFromX(_mapPosition.getXFromPixels(_popupMenuX, getWidth())));
			}});
		newPointItem.setEnabled(true);
		_popup.add(newPointItem);
	}


	/**
	 * Zoom to fit the current data area
	 */
	private void zoomToFit()
	{
		_latRange = _track.getLatRange();
		_lonRange = _track.getLonRange();
		_xRange = new DoubleRange(MapUtils.getXFromLongitude(_lonRange.getMinimum()),
			MapUtils.getXFromLongitude(_lonRange.getMaximum()));
		_yRange = new DoubleRange(MapUtils.getYFromLatitude(_latRange.getMinimum()),
			MapUtils.getYFromLatitude(_latRange.getMaximum()));
		_mapPosition.zoomToXY(_xRange.getMinimum(), _xRange.getMaximum(), _yRange.getMinimum(), _yRange.getMaximum(),
				getWidth(), getHeight());
	}


	/**
	 * Paint method
	 * @see java.awt.Canvas#paint(java.awt.Graphics)
	 */
	public void paint(Graphics inG)
	{
		super.paint(inG);
		if (_mapImage != null && (_mapImage.getWidth() != getWidth() || _mapImage.getHeight() != getHeight())) {
			_mapImage = null;
		}
		if (_track.getNumPoints() > 0)
		{
			// Check for autopan if enabled / necessary
			if (_autopanCheckBox.isSelected())
			{
				int selectedPoint = _selection.getCurrentPointIndex();
				if (selectedPoint > 0 && _dragFromX == -1 && selectedPoint != _prevSelectedPoint)
				{
					int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getXNew(selectedPoint));
					int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getYNew(selectedPoint));
					int panX = 0;
					int panY = 0;
					if (px < PAN_DISTANCE) {
						panX = px - AUTOPAN_DISTANCE;
					}
					else if (px > (getWidth()-PAN_DISTANCE)) {
						panX = AUTOPAN_DISTANCE + px - getWidth();
					}
					if (py < PAN_DISTANCE) {
						panY = py - AUTOPAN_DISTANCE;
					}
					if (py > (getHeight()-PAN_DISTANCE)) {
						panY = AUTOPAN_DISTANCE + py - getHeight();
					}
					if (panX != 0 || panY != 0) {
						_mapPosition.pan(panX, panY);
					}
				}
				_prevSelectedPoint = selectedPoint;
			}

			// Draw the mapImage if necessary
			if ((_mapImage == null || _recalculate)) {
				getMapTiles();
			}
			// Draw the prepared image onto the panel
			if (_mapImage != null) {
				inG.drawImage(_mapImage, 0, 0, getWidth(), getHeight(), null);
			}
			// Draw the zoom rectangle if necessary
			if (_zoomDragging)
			{
				inG.setColor(Color.RED);
				inG.drawLine(_dragFromX, _dragFromY, _dragFromX, _dragToY);
				inG.drawLine(_dragFromX, _dragFromY, _dragToX, _dragFromY);
				inG.drawLine(_dragToX, _dragFromY, _dragToX, _dragToY);
				inG.drawLine(_dragFromX, _dragToY, _dragToX, _dragToY);
			}
		}
		else
		{
			inG.setColor(COLOR_BG);
			inG.fillRect(0, 0, getWidth(), getHeight());
			inG.setColor(Color.GRAY);
			inG.drawString(I18nManager.getText("display.nodata"), 50, getHeight()/2);
		}
		// Draw slider etc on top
		paintChildren(inG);
	}


	/**
	 * Get the map tiles for the current zoom level and given tile parameters
	 */
	private void getMapTiles()
	{
		if (_mapImage == null || _mapImage.getWidth() != getWidth() || _mapImage.getHeight() != getHeight())
		{
			_mapImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
		}

		// Clear map
		Graphics g = _mapImage.getGraphics();
		// Clear to white
		g.setColor(COLOR_BG);
		g.fillRect(0, 0, getWidth(), getHeight());

		// reset error message
		if (!_mapCheckBox.isSelected()) {_shownOsmErrorAlready = false;}
		// Only get map tiles if selected
		if (_mapCheckBox.isSelected())
		{
			// init tile cacher
			_tileCacher.centreMap(_mapPosition.getZoom(), _mapPosition.getCentreTileX(), _mapPosition.getCentreTileY());

			boolean loadingFailed = false;
			if (_mapImage == null) return;

			// Loop over tiles drawing each one
			int[] tileIndices = _mapPosition.getTileIndices(getWidth(), getHeight());
			int[] pixelOffsets = _mapPosition.getDisplayOffsets(getWidth(), getHeight());
			for (int tileX = tileIndices[0]; tileX <= tileIndices[1] && !loadingFailed; tileX++)
			{
				int x = (tileX - tileIndices[0]) * 256 - pixelOffsets[0];
				for (int tileY = tileIndices[2]; tileY <= tileIndices[3]; tileY++)
				{
					int y = (tileY - tileIndices[2]) * 256 - pixelOffsets[1];
					Image image = _tileCacher.getTile(tileX, tileY);
					if (image != null) {
						g.drawImage(image, x, y, 256, 256, null);
					}
				}
			}

			// Make maps brighter / fainter
			float[] scaleFactors = {1.0f, 1.05f, 1.1f, 1.2f, 1.6f, 2.0f};
			float scaleFactor = scaleFactors[_transparencySlider.getValue()];
			if (scaleFactor > 1.0f) {
				RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
				hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				RescaleOp op = new RescaleOp(scaleFactor, 0, hints);
				op.filter(_mapImage, _mapImage);
			}
		}

		int pointsPainted = 0;
		// draw track points
		g.setColor(COLOR_POINT);
		int prevX = -1, prevY = -1;
		boolean connectPoints = _connectCheckBox.isSelected();
		for (int i=0; i<_track.getNumPoints(); i++)
		{
			int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getXNew(i));
			int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getYNew(i));
			if (px >= 0 && px < getWidth() && py >= 0 && py < getHeight())
			{
				if (!_track.getPoint(i).isWaypoint())
				{
					g.drawRect(px-2, py-2, 3, 3);
					// Connect track points
					if (connectPoints && prevX != -1 && prevY != -1 && !_track.getPoint(i).getSegmentStart()) {
						g.drawLine(prevX, prevY, px, py);
					}
					pointsPainted++;
					prevX = px; prevY = py;
				}
			}
			else {
				prevX = -1; prevY = -1;
			}
		}

		// Loop over points, just drawing blobs for waypoints
		g.setColor(COLOR_WAYPT_NAME);
		FontMetrics fm = g.getFontMetrics();
		int nameHeight = fm.getHeight();
		int width = getWidth();
		int height = getHeight();
		for (int i=0; i<_track.getNumPoints(); i++)
		{
			if (_track.getPoint(i).isWaypoint())
			{
				int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getXNew(i));
				int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getYNew(i));
				if (px >= 0 && px < getWidth() && py >= 0 && py < getHeight())
				{
					g.fillRect(px-3, py-3, 6, 6);
					pointsPainted++;
				}
			}
		}
		// Loop over points again, now draw names for waypoints
		for (int i=0; i<_track.getNumPoints(); i++)
		{
			if (_track.getPoint(i).isWaypoint())
			{
				int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getXNew(i));
				int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getYNew(i));
				if (px >= 0 && px < getWidth() && py >= 0 && py < getHeight())
				{
					// Figure out where to draw waypoint name so it doesn't obscure track
					String waypointName = _track.getPoint(i).getWaypointName();
					int nameWidth = fm.stringWidth(waypointName);
					boolean drawnName = false;
					// Make arrays for coordinates right left up down
					int[] nameXs = {px + 2, px - nameWidth - 2, px - nameWidth/2, px - nameWidth/2};
					int[] nameYs = {py + (nameHeight/2), py + (nameHeight/2), py - 2, py + nameHeight + 2};
					for (int extraSpace = 4; extraSpace < 13 && !drawnName; extraSpace+=2)
					{
						// Shift arrays for coordinates right left up down
						nameXs[0] += 2; nameXs[1] -= 2;
						nameYs[2] -= 2; nameYs[3] += 2;
						// Check each direction in turn right left up down
						for (int a=0; a<4; a++)
						{
							if (nameXs[a] > 0 && (nameXs[a] + nameWidth) < width
								&& nameYs[a] < height && (nameYs[a] - nameHeight) > 0
								&& !overlapsPoints(nameXs[a], nameYs[a], nameWidth, nameHeight))
							{
								// Found a rectangle to fit - draw name here and quit
								g.drawString(waypointName, nameXs[a], nameYs[a]);
								drawnName = true;
								break;
							}
						}
					}
				}
			}
		}
		// Loop over points, drawing blobs for photo points
		g.setColor(COLOR_PHOTO_PT);
		for (int i=0; i<_track.getNumPoints(); i++)
		{
			if (_track.getPoint(i).getPhoto() != null)
			{
				int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getXNew(i));
				int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getYNew(i));
				if (px >= 0 && px < getWidth() && py >= 0 && py < getHeight())
				{
					g.drawRect(px-1, py-1, 2, 2);
					g.drawRect(px-2, py-2, 4, 4);
					pointsPainted++;
				}
			}
		}

		// Draw selected range
		if (_selection.hasRangeSelected())
		{
			g.setColor(COLOR_CURR_RANGE);
			for (int i=_selection.getStart(); i<=_selection.getEnd(); i++)
			{
				int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getXNew(i));
				int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getYNew(i));
				g.drawRect(px-1, py-1, 2, 2);
			}
		}

		// Draw selected point, crosshairs
		int selectedPoint = _selection.getCurrentPointIndex();
		if (selectedPoint >= 0)
		{
			int px = getWidth() / 2 + _mapPosition.getXFromCentre(_track.getXNew(selectedPoint));
			int py = getHeight() / 2 + _mapPosition.getYFromCentre(_track.getYNew(selectedPoint));
			g.setColor(COLOR_CROSSHAIRS);
			// crosshairs
			g.drawLine(px, 0, px, getHeight());
			g.drawLine(0, py, getWidth(), py);
			// oval
			g.drawOval(px - 2, py - 2, 4, 4);
			g.drawOval(px - 3, py - 3, 6, 6);
		}

		// free g
		g.dispose();

		_recalculate = false;
		// Zoom to fit if no points found
		if (pointsPainted <= 0 && _checkBounds) {
			zoomToFit();
			_recalculate = true;
			repaint();
		}
		_checkBounds = false;
		// enable / disable transparency slider
		_transparencySlider.setEnabled(_mapCheckBox.isSelected());
	}


	/**
	 * Tests whether there are any dark pixels within the specified x,y rectangle
	 * @param inX left X coordinate
	 * @param inY bottom Y coordinate
	 * @param inWidth width of rectangle
	 * @param inHeight height of rectangle
	 * @return true if there's at least one data point in the rectangle
	 */
	private boolean overlapsPoints(int inX, int inY, int inWidth, int inHeight)
	{
		// each of the colour channels must be brighter than this to count as empty
		final int BRIGHTNESS_LIMIT = 210;
		try
		{
			// loop over x coordinate of rectangle
			for (int x=0; x<inWidth; x++)
			{
				// loop over y coordinate of rectangle
				for (int y=0; y<inHeight; y++)
				{
					int pixelColor = _mapImage.getRGB(inX + x, inY - y);
					// split into four components rgba
					int lowestBit = pixelColor & 255;
					int secondBit = (pixelColor >> 8) & 255;
					int thirdBit = (pixelColor >> 16) & 255;
					//int fourthBit = (pixelColor >> 24) & 255; // alpha ignored
					if (lowestBit < BRIGHTNESS_LIMIT || secondBit < BRIGHTNESS_LIMIT || thirdBit < BRIGHTNESS_LIMIT) return true;
				}
			}
		}
		catch (NullPointerException e) {
			// ignore null pointers, just return false
		}
		return false;
	}


	/**
	 * Inform that tiles have been updated and the map can be repainted
	 * @param isOK true if data loaded ok, false for error
	 */
	public synchronized void tilesUpdated(boolean inIsOk)
	{
		// Show message if loading failed (but not too many times)
		if (!inIsOk && !_shownOsmErrorAlready)
		{
			_shownOsmErrorAlready = true;
			// use separate thread to show message about failing to load osm images
			new Thread(new Runnable() {
				public void run() {
					try {Thread.sleep(500);} catch (InterruptedException ie) {}
					JOptionPane.showMessageDialog(MapCanvas.this,
						I18nManager.getText("error.osmimage.failed"),
						I18nManager.getText("error.osmimage.dialogtitle"),
						JOptionPane.ERROR_MESSAGE);
				}
			}).start();
		}
		_recalculate = true;
		repaint();
	}

	/**
	 * Zoom out, if not already at minimum zoom
	 */
	public void zoomOut()
	{
		_mapPosition.zoomOut();
		_recalculate = true;
		repaint();
	}

	/**
	 * Zoom in, if not already at maximum zoom
	 */
	public void zoomIn()
	{
		_mapPosition.zoomIn();
		_recalculate = true;
		repaint();
	}

	/**
	 * Pan map
	 * @param inDeltaX x shift
	 * @param inDeltaY y shift
	 */
	public void panMap(int inDeltaX, int inDeltaY)
	{
		_mapPosition.pan(inDeltaX, inDeltaY);
		_recalculate = true;
		repaint();
	}

	/**
	 * @see javax.swing.JComponent#getMinimumSize()
	 */
	public Dimension getMinimumSize()
	{
		final Dimension minSize = new Dimension(512, 300);
		return minSize;
	}

	/**
	 * @see javax.swing.JComponent#getPreferredSize()
	 */
	public Dimension getPreferredSize()
	{
		return getMinimumSize();
	}


	/**
	 * Respond to mouse click events
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent inE)
	{
		if (_track != null && _track.getNumPoints() > 0)
		{
			 // select point if it's a left-click
			if (!inE.isMetaDown())
			{
				int pointIndex = _track.getNearestPointIndexNew(
					 _mapPosition.getXFromPixels(inE.getX(), getWidth()),
					 _mapPosition.getYFromPixels(inE.getY(), getHeight()),
					 _mapPosition.getBoundsFromPixels(CLICK_SENSITIVITY), false);
				_selection.selectPoint(pointIndex);
			}
			else
			{
				// show the popup menu for right-clicks
				_popupMenuX = inE.getX();
				_popupMenuY = inE.getY();
				_popup.show(this, _popupMenuX, _popupMenuY);
			}
		}
	}

	/**
	 * Ignore mouse enter events
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	public void mouseEntered(MouseEvent inE)
	{
		// ignore
	}

	/**
	 * Ignore mouse exited events
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	public void mouseExited(MouseEvent inE)
	{
		// ignore
	}

	/**
	 * Ignore mouse pressed events
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	public void mousePressed(MouseEvent inE)
	{
		// ignore
	}

	/**
	 * Respond to mouse released events
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	public void mouseReleased(MouseEvent inE)
	{
		_recalculate = true;
		if (_zoomDragging && Math.abs(_dragToX - _dragFromX) > 20 && Math.abs(_dragToY - _dragFromY) > 20)
		{
			//System.out.println("Finished zoom: " + _dragFromX + ", " + _dragFromY + " to " + _dragToX + ", " + _dragToY);
			_mapPosition.zoomToPixels(_dragFromX, _dragToX, _dragFromY, _dragToY, getWidth(), getHeight());
		}
		_dragFromX = _dragFromY = -1;
		_zoomDragging = false;
		repaint();
	}

	/**
	 * Respond to mouse drag events
	 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
	 */
	public void mouseDragged(MouseEvent inE)
	{
		if (!inE.isMetaDown())
		{
			// Left mouse drag - pan map by appropriate amount
			_zoomDragging = false;
			if (_dragFromX != -1)
			{
				panMap(_dragFromX - inE.getX(), _dragFromY - inE.getY());
				_recalculate = true;
				repaint();
			}
			_dragFromX = inE.getX();
			_dragFromY = inE.getY();
		}
		else
		{
			// Right-click and drag - draw rectangle and control zoom
			_zoomDragging = true;
			if (_dragFromX == -1) {
				_dragFromX = inE.getX();
				_dragFromY = inE.getY();
			}
			_dragToX = inE.getX();
			_dragToY = inE.getY();
			repaint();
		}
	}

	/**
	 * Respond to mouse move events without button pressed
	 * @param inEvent ignored
	 */
	public void mouseMoved(MouseEvent inEvent)
	{
		// ignore
	}

	/**
	 * Respond to status bar message from broker
	 * @param inMessage message, ignored
	 */
	public void actionCompleted(String inMessage)
	{
		// ignore
	}

	/**
	 * Respond to data updated message from broker
	 * @param inUpdateType type of update
	 */
	public void dataUpdated(byte inUpdateType)
	{
		_recalculate = true;
		if ((inUpdateType & DataSubscriber.DATA_ADDED_OR_REMOVED) > 0) {
			_checkBounds = true;
		}
		repaint();
		// enable or disable components
		boolean hasData = _track.getNumPoints() > 0;
		_topPanel.setVisible(hasData);
		_sidePanel.setVisible(hasData);
		// grab focus for the key presses
		this.requestFocus();
	}

	/**
	 * Respond to key presses on the map canvas
	 * @param inE key event
	 */
	public void keyPressed(KeyEvent inE)
	{
		int code = inE.getKeyCode();
		// Check for meta key
		if (inE.isControlDown())
		{
			// Check for arrow keys to zoom in and out
			if (code == KeyEvent.VK_UP)
				zoomIn();
			else if (code == KeyEvent.VK_DOWN)
				zoomOut();
			// Key nav for next/prev point
			else if (code == KeyEvent.VK_LEFT)
				_selection.selectPreviousPoint();
			else if (code == KeyEvent.VK_RIGHT)
				_selection.selectNextPoint();
		}
		else
		{
			// Check for arrow keys to pan
			int upwardsPan = 0;
			if (code == KeyEvent.VK_UP)
				upwardsPan = -PAN_DISTANCE;
			else if (code == KeyEvent.VK_DOWN)
				upwardsPan = PAN_DISTANCE;
			int rightwardsPan = 0;
			if (code == KeyEvent.VK_RIGHT)
				rightwardsPan = PAN_DISTANCE;
			else if (code == KeyEvent.VK_LEFT)
				rightwardsPan = -PAN_DISTANCE;
			panMap(rightwardsPan, upwardsPan);
			// Check for delete key to delete current point
			if (code == KeyEvent.VK_DELETE && _selection.getCurrentPointIndex() >= 0)
			{
				_app.deleteCurrentPoint();
			}
		}
	}

	/**
	 * @param inE key released event, ignored
	 */
	public void keyReleased(KeyEvent e)
	{
		// ignore
	}

	/**
	 * @param inE key typed event, ignored
	 */
	public void keyTyped(KeyEvent inE)
	{
		// ignore
	}

	/**
	 * @param inE mouse wheel event indicating scroll direction
	 */
	public void mouseWheelMoved(MouseWheelEvent inE)
	{
		int clicks = inE.getWheelRotation();
		if (clicks < 0)
			zoomIn();
		else if (clicks > 0)
			zoomOut();
	}
}