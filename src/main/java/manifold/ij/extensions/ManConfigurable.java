/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;

import manifold.ij.core.ManLibraryChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManConfigurable implements Configurable
{
  private final Project _project;
  private ManifoldPanel _manifoldPanel;

  public ManConfigurable( Project project )
  {
    _project = project;
  }

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
    boolean dumbProcessorMode = !_manifoldPanel.getMode().isSelected();
    if( ManJavaLexer.isDumbPreprocessorMode() != dumbProcessorMode )
    {
      ManJavaLexer.setDumbPreprocessorMode( dumbProcessorMode );
    }

    boolean experimentalFeaturesEnabled = _manifoldPanel.getExperimentalFeatures().isSelected();
    if( ManResolveCache.isExperimentalFeaturesEnabled() != experimentalFeaturesEnabled )
    {
      ManResolveCache.setExperimentalFeaturesEnabled( experimentalFeaturesEnabled );
    }

    boolean suppressVersionCheckEnabled = _manifoldPanel.getSuppressManifoldVersionCheck().isSelected();
    if( ManLibraryChecker.isSuppressVersionCheck() != suppressVersionCheckEnabled )
    {
      ManLibraryChecker.setSuppressVersionCheck( suppressVersionCheckEnabled );
    }

    String text = _manifoldPanel.getAndroidBuildDirectory();
    PropertiesComponent state = PropertiesComponent.getInstance( _project );
    state.setValue( "manifold.android.build.dir", text );
  }

  private class ManifoldPanel extends JPanel
  {
    private JCheckBox _mode;
    private JCheckBox _experimentalFeatures;
    private JCheckBox _suppressManifoldVersionCheck;
    private TextFieldWithBrowseButton _androidBuildDirectory;
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
      _mode.setToolTipText( "When in smart mode the preprocessor actively shades inactive " +
                            "code according to both local and environmental definitions. " +
                            "Additionally inactive code is not parsed by IntelliJ to avoid " +
                            "compiler errors and to avoid false positives wrt usage searches etc." );
      _mode.setSelected( !ManJavaLexer.isDumbPreprocessorMode() );
      _mode.addChangeListener( e -> _modified = true );

      c.gridy = y++;
      add( _experimentalFeatures = new JCheckBox( "Enable experimental features" ), c );
      _experimentalFeatures.setSelected( ManResolveCache.isExperimentalFeaturesEnabled() );
      _experimentalFeatures.addChangeListener( e -> _modified = true );

      c.gridy = y++;
      add( _suppressManifoldVersionCheck = new JCheckBox( "Suppress Manifold version check" ), c );
      _suppressManifoldVersionCheck.setSelected( ManLibraryChecker.isSuppressVersionCheck() );
      _suppressManifoldVersionCheck.addChangeListener( e -> _modified = true );

      c.gridy = y++;
      c.insets = JBUI.insets( 5, 2, 0, 0 );
      JLabel label = new JLabel( "Android custom build directory:" );
      String desc =
        "For android projects using manifold-preprocessor with build variants plugin. " +
        "If you override the standard `build` directory location, you must specify the " +
        "directory here. Otherwise, build variants will not work with the preprocessor.";
      label.setToolTipText( desc );
      add( label, c );
      c.gridy = y++;
      c.weightx = 1;
      c.insets = JBUI.insets( 2, 2, 0, 0 );
      add( _androidBuildDirectory = makeAndroidBuildDirectoryUi( desc ), c );
      PropertiesComponent state = PropertiesComponent.getInstance( _project );
      String dir = state.getValue( "manifold.android.build.dir" );
      _androidBuildDirectory.setText( dir );
      _androidBuildDirectory.getTextField().getDocument().addDocumentListener( new DocumentAdapter()
        {
          @Override
          protected void textChanged( @NotNull DocumentEvent e )
          {
            _modified = true;
          }
        } );
      c.weightx = 0;

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

    private TextFieldWithBrowseButton makeAndroidBuildDirectoryUi( String description )
    {
      TextFieldWithBrowseButton directoryChooser = new TextFieldWithBrowseButton();
      FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(
        false, // choose files
        true,  // choose directories
        false,
        false,
        false,
        false
      );
      directoryChooser.setPreferredSize( new Dimension( 300, directoryChooser.getPreferredSize().height ) );
      directoryChooser.addBrowseFolderListener( "Android `build` Directory", description, _project, chooserDescriptor );
      return directoryChooser;
    }

    JCheckBox getMode()
    {
      return _mode;
    }

    JCheckBox getExperimentalFeatures()
    {
      return _experimentalFeatures;
    }

    JCheckBox getSuppressManifoldVersionCheck()
    {
      return _suppressManifoldVersionCheck;
    }

    String getAndroidBuildDirectory()
    {
      return _androidBuildDirectory.getText();
    }

    boolean isModified()
    {
      return _modified;
    }
  }
}
