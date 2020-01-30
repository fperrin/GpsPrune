package tim.prune.gui;

import java.util.ArrayList;
import javax.swing.AbstractListModel;

import tim.prune.data.DataPoint;
import tim.prune.data.Track;
import tim.prune.I18nManager;

/**
 * Class to act as list model for the segment list
 */
public class SegmentListModel extends AbstractListModel<String>
{
	Track _track = null;
	ArrayList<DataPoint> _segmentStarts = null;

	/**
	 * Constructor giving Track object
	 * @param inTrack Track object
	 */
	public SegmentListModel(Track inTrack)
	{
		_track = inTrack;
		_segmentStarts = new ArrayList<DataPoint>();
		_track.getSegmentStarts(_segmentStarts);
	}

	/**
	 * @see javax.swing.ListModel#getSize()
	 */
	public int getSize()
	{
		return _segmentStarts.size();
	}

	/**
	 * @see javax.swing.ListModel#getElementAt(int)
	 */
	public String getElementAt(int inIndex)
	{
		return I18nManager.getText("details.lists.segments.label") + (inIndex + 1) + " (" + (getSegmentStart(inIndex) + 1) + " " + I18nManager.getText("details.lists.segments.to") + " " + (getSegmentEnd(inIndex) + 1) + ")";
	}

	/**
	 * Fire event to notify that contents have changed
	 */
	public void fireChanged()
	{
		_track.getSegmentStarts(_segmentStarts);
		this.fireContentsChanged(this, 0, getSize()-1);
	}

	/**

	 */
	public int getSegmentStart(int inIndex)
	{
		return _track.getPointIndex(_segmentStarts.get(inIndex));
	}

	public int getSegmentEnd(int inIndex)
	{
		if (inIndex < getSize() - 1) {
			return _track.getPointIndex(_segmentStarts.get(inIndex + 1)) - 1;
		}
		else
		{
			return _track.getNumPoints() - 1;
		}
	}
}
