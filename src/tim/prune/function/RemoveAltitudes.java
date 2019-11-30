package tim.prune.function;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import tim.prune.App;
import tim.prune.GenericFunction;
import tim.prune.I18nManager;
import tim.prune.config.Config;
import tim.prune.data.Field;
import tim.prune.data.Unit;
import tim.prune.data.UnitSetLibrary;

/**
 * Class to provide the function to add remove the altitude from points in a
 * track range
 */
public class RemoveAltitudes extends GenericFunction
{
	/**
	 * Constructor
	 * @param inApp application object for callback
	 */
	public RemoveAltitudes(App inApp)
	{
		super(inApp);
	}

	/** Get the name key */
	public String getNameKey() {
		return "function.removealtitudes";
	}

	/**
	 * Begin the function
	 */
	public void begin()
	{
		int selStart = _app.getTrackInfo().getSelection().getStart();
		int selEnd = _app.getTrackInfo().getSelection().getEnd();
		if (!_app.getTrackInfo().getTrack().hasData(Field.ALTITUDE, selStart, selEnd))
		{
			_app.showErrorMessage(getNameKey(), "dialog.addaltitude.noaltitudes");
			return;
		}
		_app.removeAltitudes(selStart, selEnd);
	}
}
