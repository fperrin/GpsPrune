package tim.prune.function.srtm;

import java.util.ArrayList;

import javax.swing.JOptionPane;

import tim.prune.App;
import tim.prune.DataSubscriber;
import tim.prune.GenericFunction;
import tim.prune.I18nManager;
import tim.prune.UpdateMessageBroker;
import tim.prune.data.Altitude;
import tim.prune.data.DataPoint;
import tim.prune.data.Field;
import tim.prune.data.Track;
import tim.prune.data.UnitSetLibrary;
import tim.prune.gui.ProgressDialog;
import tim.prune.undo.UndoLookupSrtm;

/**
 * Class to provide a lookup function for point altitudes using the Space
 * Shuttle's SRTM data files. HGT files are downloaded into memory via HTTP and
 * point altitudes can then be interpolated from the 3m grid data.
 */
public class LookupSrtmFunction extends GenericFunction implements Runnable
{
	/** Progress dialog */
	private ProgressDialog _progress = null;
	/** Track to process */
	private Track _track = null;
	/** Flag for whether this is a real track or a terrain one */
	private boolean _normalTrack = true;
	/** Flag to check whether this function is currently running or not */
	private boolean _running = false;

	/** Altitude below which is considered void */
	private static final int VOID_VAL = -32768;

	/**
	 * Constructor
	 * @param inApp  App object
	 */
	public LookupSrtmFunction(App inApp) {
		super(inApp);
	}

	/** @return name key */
	public String getNameKey() {
		return "function.lookupsrtm";
	}

	/**
	 * Begin the lookup using the normal track
	 */
	public void begin() {
		begin(_app.getTrackInfo().getTrack(), true);
	}

	/**
	 * Begin the lookup with an alternative track
	 * @param inAlternativeTrack
	 */
	public void begin(Track inAlternativeTrack) {
		begin(inAlternativeTrack, false);
	}

	/**
	 * Begin the function with the given parameters
	 * @param inTrack track to process
	 * @param inNormalTrack true if this is a "normal" track, false for an artificially constructed one such as for terrain
	 */
	private void begin(Track inTrack, boolean inNormalTrack)
	{
		_running = true;
		if (! SrtmDiskCache.ensureCacheIsUsable())
		{
			_app.showErrorMessage(getNameKey(), "error.cache.notthere");
		}
		if (_progress == null) {
			_progress = new ProgressDialog(_parentFrame, getNameKey());
		}
		_progress.show();
		_track = inTrack;
		_normalTrack = inNormalTrack;
		// start new thread for time-consuming part
		new Thread(this).start();
	}

	/**
	 * Run method using separate thread
	 */
	public void run()
	{
		// Compile list of tiles to get
		ArrayList<SrtmTile> tileList = new ArrayList<SrtmTile>();
		boolean hasZeroAltitudePoints = false;
		boolean hasNonZeroAltitudePoints = false;
		// First, loop to see what kind of points we have
		for (int i = 0; i < _track.getNumPoints(); i++)
		{
			if (_track.getPoint(i).hasAltitude())
			{
				if (_track.getPoint(i).getAltitude().getValue() == 0) {
					hasZeroAltitudePoints = true;
				}
				else {
					hasNonZeroAltitudePoints = true;
				}
			}
		}
		// Should we overwrite the zero altitude values?
		boolean overwriteZeros = hasZeroAltitudePoints && !hasNonZeroAltitudePoints;
		// If non-zero values present as well, ask user whether to overwrite the zeros or not
		if (hasNonZeroAltitudePoints && hasZeroAltitudePoints && JOptionPane.showConfirmDialog(_parentFrame,
			I18nManager.getText("dialog.lookupsrtm.overwritezeros"), I18nManager.getText(getNameKey()),
			JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
		{
			overwriteZeros = true;
		}

		// Now loop again to extract the required tiles
		for (int i = 0; i < _track.getNumPoints(); i++)
		{
			// Consider points which don't have altitudes or have zero values
			if (needsAltitude(_track.getPoint(i), overwriteZeros))
			{
				SrtmTile tile = new SrtmTile(_track.getPoint(i));
				boolean alreadyGot = false;
				for (int t = 0; t < tileList.size(); t++)
				{
					if (tileList.get(t).equals(tile)) {
						alreadyGot = true;
					}
				}
				if (!alreadyGot) {tileList.add(tile);}
			}
		}
		lookupValues(tileList, overwriteZeros);
		// Finished
		_running = false;
	}

	/**
	 * true if we need to set the altitude of this point
	 */
	private boolean needsAltitude(DataPoint point, boolean overwriteZeros)
	{
		if (!point.hasAltitude())
		{
			return true;
		}
		if (overwriteZeros && point.getAltitude().getValue() == 0)
		{
			return true;
		}
		return false;
	}

	/**
	 * Lookup the values from SRTM data
	 * @param inTileList list of tiles to get
	 * @param inOverwriteZeros true to overwrite zero altitude values
	 */
	private void lookupValues(ArrayList<SrtmTile> inTileList, boolean inOverwriteZeros)
	{
		UndoLookupSrtm undo = new UndoLookupSrtm(_app.getTrackInfo());
		int numAltitudesFound = 0;
		// Update progress bar
		if (_progress != null)
		{
			_progress.setMaximum(inTileList.size());
			_progress.setValue(0);
		}
		String errorMessage = "";
		for (int t=0; t<inTileList.size() && !_progress.isCancelled(); t++)
		{
			SrtmTile tile = inTileList.get(t);
			SrtmSource srtmSource = tile.findBestCachedSource();

			if (srtmSource == null)
			{
				errorMessage += "Tile "+tile.getTileName()+" not in cache!\n";
				continue;
			}

			// Set progress
			_progress.setValue(t);

			int[] heights;
			try {
				heights = srtmSource.getTileHeights(tile);
			}
			catch (SrtmSourceException e)
			{
				errorMessage += e.getMessage();
				continue;
			}
			int rowSize = srtmSource.getRowSize(tile);
			if (rowSize <= 0)
			{
				errorMessage += "Tile "+tile.getTileName()+" is corrupted";
			}

			// Loop over all points in track, try to apply altitude from array
			for (int p = 0; p < _track.getNumPoints(); p++)
			{
				DataPoint point = _track.getPoint(p);
				if (needsAltitude(point, inOverwriteZeros))
				{
					if (new SrtmTile(point).equals(tile))
					{
						double x = (point.getLongitude().getDouble() - tile.getLongitude()) * (rowSize - 1);
						double y = rowSize - (point.getLatitude().getDouble() - tile.getLatitude()) * (rowSize - 1);
						int idx1 = ((int)y)*rowSize + (int)x;
						try
						{
							int[] fouralts = {heights[idx1], heights[idx1+1], heights[idx1-rowSize], heights[idx1-rowSize+1]};
							int numVoids = (fouralts[0]==VOID_VAL?1:0) + (fouralts[1]==VOID_VAL?1:0)
								+ (fouralts[2]==VOID_VAL?1:0) + (fouralts[3]==VOID_VAL?1:0);
							// if (numVoids > 0) System.out.println(numVoids + " voids found");
							double altitude = 0.0;
							switch (numVoids)
							{
							case 0:	altitude = bilinearInterpolate(fouralts, x, y); break;
							case 1: altitude = bilinearInterpolate(fixVoid(fouralts), x, y); break;
							case 2:
							case 3: altitude = averageNonVoid(fouralts); break;
							default: altitude = VOID_VAL;
							}
							// Special case for terrain tracks, don't interpolate voids yet
							if (!_normalTrack && numVoids > 0) {
								altitude = VOID_VAL;
							}
							if (altitude != VOID_VAL)
							{
								point.setFieldValue(Field.ALTITUDE, ""+altitude, false);
								// depending on settings, this value may have been added as feet, we need to force metres
								point.getAltitude().reset(new Altitude((int)altitude, UnitSetLibrary.UNITS_METRES));
								numAltitudesFound++;
							}
						}
						catch (ArrayIndexOutOfBoundsException obe) {
							errorMessage += "Point not in tile? lat=" + point.getLatitude().getDouble() + ", x=" + x + ", y=" + y + ", idx=" + idx1+"\n";
						}
					}
				}
			}
		}

		_progress.dispose();
		if (_progress.isCancelled()) {
			return;
		}

		if (! errorMessage.equals("")) {
			_app.showErrorMessageNoLookup(getNameKey(), errorMessage);
			return;
		}
		if (numAltitudesFound > 0)
		{
			// Inform app including undo information
			_track.requestRescale();
			UpdateMessageBroker.informSubscribers(DataSubscriber.DATA_ADDED_OR_REMOVED);
			// Don't update app if we're doing another track
			if (_normalTrack)
			{
				_app.completeFunction(undo,
					I18nManager.getTextWithNumber("confirm.lookupsrtm", numAltitudesFound));
			}
		}
		else if (inTileList.size() > 0) {
			_app.showErrorMessage(getNameKey(), "error.lookupsrtm.nonefound");
		}
		else {
			_app.showErrorMessage(getNameKey(), "error.lookupsrtm.nonerequired");
		}
	}

	/**
	 * Perform a bilinear interpolation on the given altitude array
	 * @param inAltitudes array of four altitude values on corners of square (bl, br, tl, tr)
	 * @param inX x coordinate
	 * @param inY y coordinate
	 * @return interpolated altitude
	 */
	private static double bilinearInterpolate(int[] inAltitudes, double inX, double inY)
	{
		double alpha = inX - (int) inX;
		double beta  = 1 - (inY - (int) inY);
		double alt = (1-alpha)*(1-beta)*inAltitudes[0] + alpha*(1-beta)*inAltitudes[1]
			+ (1-alpha)*beta*inAltitudes[2] + alpha*beta*inAltitudes[3];
		return alt;
	}

	/**
	 * Fix a single void in the given array by replacing it with the average of the others
	 * @param inAltitudes array of altitudes containing one void
	 * @return fixed array without voids
	 */
	private static int[] fixVoid(int[] inAltitudes)
	{
		int[] fixed = new int[inAltitudes.length];
		for (int i = 0; i < inAltitudes.length; i++)
		{
			if (inAltitudes[i] == VOID_VAL) {
				fixed[i] = (int) Math.round(averageNonVoid(inAltitudes));
			}
			else {
				fixed[i] = inAltitudes[i];
			}
		}
		return fixed;
	}

	/**
	 * Calculate the average of the non-void altitudes in the given array
	 * @param inAltitudes array of altitudes with one or more voids
	 * @return average of non-void altitudes
	 */
	private static final double averageNonVoid(int[] inAltitudes)
	{
		double totalAltitude = 0.0;
		int numAlts = 0;
		for (int i = 0; i < inAltitudes.length; i++)
		{
			if (inAltitudes[i] != VOID_VAL)
			{
				totalAltitude += inAltitudes[i];
				numAlts++;
			}
		}
		if (numAlts < 1) {return VOID_VAL;}
		return totalAltitude / numAlts;
	}

	/**
	 * @return true if a thread is currently running
	 */
	public boolean isRunning()
	{
		return _running;
	}
}
