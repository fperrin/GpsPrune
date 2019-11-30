package tim.prune.function.srtm;

import java.util.ArrayList;

import javax.swing.JOptionPane;

import tim.prune.App;
import tim.prune.GenericFunction;
import tim.prune.GpsPrune;
import tim.prune.I18nManager;
import tim.prune.data.DoubleRange;
import tim.prune.gui.ProgressDialog;

/**
 * Class to provide a download function for the Space Shuttle's SRTM data files.
 * HGT files are downloaded into memory via HTTP and stored in the map cache.
 */
public class DownloadSrtmFunction extends GenericFunction implements Runnable
{
	/** Progress dialog */
	private ProgressDialog _progress = null;
	/** Flag to check whether this function is currently running or not */
	private boolean _running = false;
	private SrtmSource _srtmSource = null;

	/**
	 * Constructor
	 * @param inApp  App object
	 */
	public DownloadSrtmFunction(App inApp, SrtmSource inSrtmSource) {
		super(inApp);
		_srtmSource = inSrtmSource;
	}

	/** @return name key */
	public String getNameKey() {
		return "function.downloadsrtm."+_srtmSource.getName();
	}

	/**
	 * Begin the download
	 */
	public void begin()
	{
		if (! SrtmDiskCache.ensureCacheIsUsable())
		{
			_app.showErrorMessage(getNameKey(), "error.downloadsrtm.nocache");
			return;
		}
		if (! _srtmSource.isReadyToUse())
		{
			_app.showErrorMessage(getNameKey(), getNameKey() + ".needsetup");
			return;
		}

		_running = true;
		if (_progress == null) {
			_progress = new ProgressDialog(_parentFrame, getNameKey());
		}
		_progress.show();
		// start new thread for time-consuming part
		new Thread(this).start();
	}

	/**
	 * Run method using separate thread
	 */
	public void run()
	{
		ArrayList<SrtmTile> tileList = buildCoveringTiles();
		downloadTiles(tileList);
		// Finished
		_running = false;
	}

	private ArrayList<SrtmTile> buildCoveringTiles()
	{
		// Compile list of tiles to get
		ArrayList<SrtmTile> tileList = new ArrayList<SrtmTile>();

		// First, loop to see which tiles are needed
		DoubleRange lonRange = _app.getTrackInfo().getTrack().getLonRange();
		DoubleRange latRange = _app.getTrackInfo().getTrack().getLatRange();
		final int minLon = (int) Math.floor(lonRange.getMinimum());
		final int maxLon = (int) Math.floor(lonRange.getMaximum());
		final int minLat = (int) Math.floor(latRange.getMinimum());
		final int maxLat = (int) Math.floor(latRange.getMaximum());

		for (int lon=minLon; lon<= maxLon; lon++)
		{
			for (int lat=minLat; lat <= maxLat; lat++)
			{
				SrtmTile tile = new SrtmTile(lat, lon);
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

		return tileList;
	}

	/**
	 * Download the tiles of SRTM data
	 * @param inTileList list of tiles to get
	 */
	private void downloadTiles(ArrayList<SrtmTile> inTileList)
	{
		String errorMessage = "";
		// Update progress bar
		if (_progress != null)
		{
			_progress.setMaximum(inTileList.size());
			_progress.setValue(0);
		}

		int numDownloaded = 0;
		for (int t=0; t<inTileList.size() && !_progress.isCancelled(); t++)
		{
			if (_srtmSource.isCached(inTileList.get(t)))
			{
				System.out.println(inTileList.get(t).getTileName()+" already in cache, nothing to do");
				continue;
			}

			boolean success;
			try
			{
				success = _srtmSource.downloadTile(inTileList.get(t));
				if (success)
				{
					numDownloaded++;
				}
				// Set progress
				_progress.setValue(t + 1);
			}
			catch (SrtmSourceException e)
			{
				errorMessage += e.getMessage();
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
		if (numDownloaded == 1)
		{
			JOptionPane.showMessageDialog(_parentFrame, I18nManager.getTextWithNumber("confirm.downloadsrtm.1", numDownloaded),
				I18nManager.getText(getNameKey()), JOptionPane.INFORMATION_MESSAGE);
		}
		else if (numDownloaded > 1)
		{
			JOptionPane.showMessageDialog(_parentFrame, I18nManager.getTextWithNumber("confirm.downloadsrtm", numDownloaded),
				I18nManager.getText(getNameKey()), JOptionPane.INFORMATION_MESSAGE);
		}
		else if (inTileList.size() > 0) {
			JOptionPane.showMessageDialog(_parentFrame, I18nManager.getText("confirm.downloadsrtm.none"));
		}
	}
}
