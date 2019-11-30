package tim.prune.function.settings;

import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Base64;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import tim.prune.App;
import tim.prune.GenericFunction;
import tim.prune.I18nManager;
import tim.prune.config.Config;
import tim.prune.function.srtm.SrtmGl1Source;
import tim.prune.function.srtm.SrtmSourceException;

/**
 * Set authentication data for the NASA Earthdata systems
 */
public class SetEarthdataAuthentication extends GenericFunction
{
	private JDialog _dialog = null;
	private JTextField _usernameField = null;
	private JPasswordField _passwordField = null;
	private JLabel _authAccepted = null;

	/**
	 * Constructor
	 * @param inApp App object
	 */
	public SetEarthdataAuthentication(App inApp) {
		super(inApp);
	}

	/** @return name key */
	public String getNameKey() {
		return "function.setearthdataauthentication";
	}

	public void begin()
	{
		if (_dialog == null)
		{
			_dialog = new JDialog(_parentFrame, I18nManager.getText(getNameKey()), true);
			_dialog.setLocationRelativeTo(_parentFrame);
			// Create Gui and show it
			_dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			_dialog.getContentPane().add(makeDialogComponents());
			_dialog.pack();
		}
		prefillCurrentAuth();
		_dialog.setVisible(true);
	}

	/**
	 * Make the dialog components
	 * @return the GUI components for the dialog
	 */
	private JPanel makeDialogComponents()
	{
		// Blurb to explain to the user
		JPanel dialogPanel = new JPanel();
		dialogPanel.setLayout(new BorderLayout());
		dialogPanel.add(new JLabel("<html>"+I18nManager.getText("dialog.earthdataauth.intro")+"</html>"), BorderLayout.NORTH);

		// username and password fields
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		JPanel usernamePasswordPanel = new JPanel();
		usernamePasswordPanel.setLayout(new GridLayout(2, 2));

		JLabel usernameLabel = new JLabel(I18nManager.getText("dialog.earthdataauth.user"));
		usernamePasswordPanel.add(usernameLabel);
		_usernameField = new JTextField("");
		usernamePasswordPanel.add(_usernameField);

		JLabel passwordLabel = new JLabel(I18nManager.getText("dialog.earthdataauth.password"));
		usernamePasswordPanel.add(passwordLabel);
		_passwordField = new JPasswordField("");
		usernamePasswordPanel.add(_passwordField);
		mainPanel.add(usernamePasswordPanel, BorderLayout.CENTER);

		JPanel authStatusPanel = new JPanel();
		authStatusPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		_authAccepted = new JLabel(" ");
		authStatusPanel.add(_authAccepted);
		mainPanel.add(authStatusPanel, BorderLayout.SOUTH);

		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 15));
		dialogPanel.add(mainPanel, BorderLayout.CENTER);

		// ok / cancel buttons at bottom
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton checkButton = new JButton(I18nManager.getText("button.check"));
		checkButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				testUsernameAndPassword();
			}
		});
		buttonPanel.add(checkButton);
		JButton okButton = new JButton(I18nManager.getText("button.ok"));
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				finish();
			}
		});
		buttonPanel.add(okButton);
		JButton cancelButton = new JButton(I18nManager.getText("button.cancel"));
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				_dialog.dispose();
			}
		});
		buttonPanel.add(cancelButton);
		dialogPanel.add(buttonPanel, BorderLayout.SOUTH);
		dialogPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 15));
		return dialogPanel;
	}

	private String getNewAuthString()
	{
		String username = _usernameField.getText();
		String password = _passwordField.getText();
		return Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
	}

	private void finish()
	{
		Config.setConfigString(Config.KEY_EARTHDATA_AUTH, getNewAuthString());
		_dialog.dispose();
	}

	private void prefillCurrentAuth()
	{
		String authString = Config.getConfigString(Config.KEY_EARTHDATA_AUTH);
		if (authString == null)
		{
			_usernameField.setText("");
			_passwordField.setText("");
		}
		String decoded = new String(Base64.getDecoder().decode(authString));
		if (decoded.contains(":"))
		{
			_usernameField.setText(decoded.split(":", 2)[0]);
			_passwordField.setText(decoded.split(":", 2)[1]);
		}
		else
		{
			_usernameField.setText("");
			_passwordField.setText("");
		}

		_authAccepted.setText(" ");
	}

	private void testUsernameAndPassword()
	{
		String username = _usernameField.getText();
		String password = _passwordField.getText();
		SrtmGl1Source srtmGL1 = new SrtmGl1Source();
		try
		{
			_authAccepted.setText("...");
			srtmGL1.testAuth(getNewAuthString());
			_authAccepted.setText(I18nManager.getText("dialog.earthdataauth.authaccepted"));
		}
		catch (SrtmSourceException e)
		{
			_authAccepted.setText(I18nManager.getText("dialog.earthdataauth.authrejected") + ": " + e.getMessage());
		}
	}
}
