package manifold.ij.extensions;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.JBUI;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class ManConfigurable implements Configurable
{
  private ManifoldPanel _manifoldPanel;

//  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName()
  {
    return "Manifold";
  }

  @Nullable
  @Override
  public JComponent createComponent()
  {
    return _manifoldPanel = new ManifoldPanel();
  }

  @Override
  public boolean isModified()
  {
    return _manifoldPanel.isModified();
  }

  @Override
  public void apply()
  {
    ManJavaLexer.setDumbPreprocessorMode( !_manifoldPanel.getMode().isSelected() );
  }

  private class ManifoldPanel extends JPanel
  {
    private JCheckBox _mode;
    private boolean _modified;

    ManifoldPanel()
    {
      super( new GridBagLayout() );

      setBorder( BorderFactory.createEmptyBorder( 8, 8, 8, 8 ) );

      GridBagConstraints c = new GridBagConstraints();

      int y = 0;

      c.anchor = GridBagConstraints.NORTHWEST;
      c.fill = GridBagConstraints.NONE;
      c.gridx = 0;
      c.gridy = y++;
      c.gridwidth = GridBagConstraints.REMAINDER;
      c.gridheight = 1;
      c.weightx = 0;
      c.weighty = 0;
      c.insets = JBUI.insets( 2, 2, 0, 0 );
      add( _mode = new JCheckBox( "Preprocessor smart mode" ), c );
      _mode.setToolTipText( "When in smart mode the preprocessor actively shades inactive\n" +
                            "code according to both local and environmental definitions.\n" +
                            "Additionally inactive code is not parsed by IntelliJ to avoid\n" +
                            "compiler errors and to avoid false positives wrt usage searches" +
                            "etc." );
      _mode.setSelected( !ManJavaLexer.isDumbPreprocessorMode() );
      _mode.addChangeListener( e -> _modified = true );

      c.anchor = GridBagConstraints.NORTHWEST;
      c.fill = GridBagConstraints.BOTH;
      c.gridx = 0;
      c.gridy = y++;
      c.gridwidth = GridBagConstraints.REMAINDER;
      c.gridheight = GridBagConstraints.REMAINDER;
      c.weightx = 1;
      c.weighty = 1;
      add( new JPanel(), c );
    }

    JCheckBox getMode()
    {
      return _mode;
    }

    boolean isModified()
    {
      return _modified;
    }
  }
}
